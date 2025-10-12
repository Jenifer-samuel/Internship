package com.anitha.remindme.face

import android.graphics.Bitmap

object FaceHashing {
    // Simple 8x8 aHash implementation
    fun averageHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var sum = 0L
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        val grays = IntArray(64)
        for (i in 0 until 64) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val gray = (r + g + b) / 3
            grays[i] = gray
            sum += gray
        }
        val avg = sum / 64
        val sb = StringBuilder()
        for (i in 0 until 64) {
            sb.append(if (grays[i] >= avg) '1' else '0')
        }
        return sb.toString()
    }

    fun hammingDistance(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }
}
