package com.anitha.remindme.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class BitmapProvider(private val latestBitmap: () -> Bitmap?) {
    operator fun invoke(): Bitmap? = latestBitmap()
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFacesDetected: (faces: Int, bitmapProvider: () -> Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            startCamera(ctx, lifecycleOwner, previewView) { faces, provider ->
                onFacesDetected(faces, provider)
            }
            previewView
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFacesDetected: (faces: Int, bitmapProvider: () -> Bitmap?) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
        )

        val analysisExecutor = Executors.newSingleThreadExecutor()
        var lastBitmap: Bitmap? = null

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    val rotated = imageProxy.toBitmap() // rotated to upright
                                    if (rotated != null) {
                                        val rect = faces.first().boundingBox
                                        lastBitmap = cropBitmapToRect(rotated, rect)
                                        onFacesDetected(faces.size) { lastBitmap }
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
        } catch (exc: Exception) {
            // no-op
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val nv21 = yuv420888ToNv21(this) ?: return null
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val jpegBytes = out.toByteArray()
    val rawBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
    val rotation = imageInfo.rotationDegrees.toFloat()
    if (rotation == 0f) return rawBitmap
    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray? {
    val yPlane = image.planes.getOrNull(0)?.buffer ?: return null
    val uPlane = image.planes.getOrNull(1)?.buffer ?: return null
    val vPlane = image.planes.getOrNull(2)?.buffer ?: return null

    val ySize = image.width * image.height
    val uvSize = image.width * image.height / 2
    val out = ByteArray(ySize + uvSize)

    val yRowStride = image.planes[0].rowStride
    val uvRowStride = image.planes[1].rowStride
    val uvPixelStride = image.planes[1].pixelStride

    // Copy Y
    yPlane.rewind()
    if (yRowStride == image.width) {
        yPlane.get(out, 0, ySize)
    } else {
        var outPos = 0
        val yBytes = ByteArray(yPlane.remaining())
        yPlane.get(yBytes)
        var yOffset = 0
        for (row in 0 until image.height) {
            System.arraycopy(yBytes, yOffset, out, outPos, image.width)
            outPos += image.width
            yOffset += yRowStride
        }
    }

    // Copy VU (NV21) from U and V planes
    uPlane.rewind()
    vPlane.rewind()
    val uBytes = ByteArray(uPlane.remaining())
    val vBytes = ByteArray(vPlane.remaining())
    uPlane.get(uBytes)
    vPlane.get(vBytes)

    var outPos = ySize
    for (row in 0 until image.height / 2) {
        var uRowOffset = row * uvRowStride
        var vRowOffset = row * uvRowStride
        for (col in 0 until image.width step 2) {
            val v = vBytes.getOrNull(vRowOffset) ?: 0
            val u = uBytes.getOrNull(uRowOffset) ?: 0
            out[outPos++] = v
            out[outPos++] = u
            uRowOffset += uvPixelStride
            vRowOffset += uvPixelStride
        }
    }

    return out
}

private fun cropBitmapToRect(source: Bitmap, rect: Rect): Bitmap {
    val left = rect.left.coerceIn(0, source.width)
    val top = rect.top.coerceIn(0, source.height)
    val right = rect.right.coerceIn(left + 1, source.width)
    val bottom = rect.bottom.coerceIn(top + 1, source.height)
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    return Bitmap.createBitmap(source, left, top, width, height)
}
