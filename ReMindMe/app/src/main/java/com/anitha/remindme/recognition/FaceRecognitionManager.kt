package com.anitha.remindme.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.anitha.remindme.data.Profile
import com.anitha.remindme.data.ProfileStore
import kotlin.math.abs

class FaceRecognitionManager(private val context: Context) {
    private val store = ProfileStore(context)
    @Volatile private var latestFaceBitmap: Bitmap? = null

    // aHash 8x8
    private fun averageHash(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var sum = 0L
        val pixels = IntArray(64)
        resized.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (r + g + b) / 3
        }
        val avg = sum / 64
        val bits = BooleanArray(64)
        val sb = StringBuilder()
        for (i in 0 until 64) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = (r + g + b) / 3
            bits[i] = gray >= avg
        }
        var value = 0
        for (i in 0 until 64) {
            value = (value shl 1) or (if (bits[i]) 1 else 0)
            if ((i + 1) % 4 == 0) {
                sb.append(value.toString(16))
                value = 0
            }
        }
        return sb.toString()
    }

    private fun hammingDistance(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        var dist = 0
        for (i in 0 until len) {
            val x = Character.digit(a[i], 16)
            val y = Character.digit(b[i], 16)
            val v = x xor y
            dist += Integer.bitCount(v)
        }
        return dist + abs(a.length - b.length) * 4
    }

    fun recognize(frameBitmap: Bitmap?): Profile? {
        if (frameBitmap == null) return null
        val hash = averageHash(frameBitmap)
        var best: Profile? = null
        var bestDist = Int.MAX_VALUE
        for (p in store.loadAll()) {
            val d = hammingDistance(hash, p.hash)
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return if (bestDist <= 10) best else null
    }

    fun setLatestFaceBitmap(bitmap: Bitmap?) {
        latestFaceBitmap = bitmap
    }

    fun captureAndEnrollCurrentFace(name: String, reminder: String) {
        val snapshot = latestFaceBitmap ?: return
        enrollFromBitmap(snapshot, name, reminder)
    }

    fun enrollFromBitmap(bitmap: Bitmap, name: String, reminder: String) {
        val hash = averageHash(bitmap)
        val profile = Profile(name = name, reminder = reminder, hash = hash)
        store.saveProfile(profile, bitmap)
    }
}
