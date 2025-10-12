package com.anitha.remindme.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.anitha.remindme.data.AppDatabase
import com.anitha.remindme.data.UserEntity
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EnrollFaceActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finishWithMessage("Camera permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.anitha.remindme.R.layout.activity_enroll_face)
        previewView = findViewById(com.anitha.remindme.R.id.preview)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(com.anitha.remindme.R.id.btnCapture).setOnClickListener { captureAndEnroll() }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPerms.launch(Manifest.permission.CAMERA)
        } else startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().setTargetRotation(previewView.display.rotation).build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                finishWithMessage("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndEnroll() {
        val output = imageCapture
        output.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = previewView.bitmap
                if (bitmap == null) {
                    image.close()
                    finishWithMessage("Failed to capture image")
                    return
                }
                val cropped = centerCrop(bitmap)
                val hash = FaceHashing.averageHash(cropped)
                enroll(hash)
                image.close()
            }
            override fun onError(exception: ImageCaptureException) {
                finishWithMessage("Capture failed: ${exception.message}")
            }
        })
    }

    private fun enroll(hash: String) {
        val db = AppDatabase.get(this)
        Executors.newSingleThreadExecutor().execute {
            val userId = intent.getLongExtra("userId", -1L)
            val user = if (userId > 0) db.userDao().getUsers().firstOrNull { it.id == userId } else db.userDao().getUsers().firstOrNull()
            if (user == null) {
                runOnUiThread { finishWithMessage("Please create a user first") }
                return@execute
            }
            db.userDao().updateFaceHash(user.id, hash)
            runOnUiThread {
                Toast.makeText(this, "Face enrolled for ${user.name}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun finishWithMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
