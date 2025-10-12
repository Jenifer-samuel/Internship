package com.anitha.remindme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class Profile(
    val name: String,
    val reminder: String,
    val hash: String
)

class ProfileStore(private val context: Context) {
    private val dir: File by lazy { File(context.filesDir, "profiles").apply { mkdirs() } }
    private val indexFile: File by lazy { File(dir, "index.json") }

    fun saveProfile(profile: Profile, faceBitmap: Bitmap?) {
        if (faceBitmap != null) {
            val imageFile = File(dir, "${'$'}{profile.name}.png")
            FileOutputStream(imageFile).use { out ->
                faceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        val profiles = loadAll().toMutableList()
        profiles.removeAll { it.name == profile.name }
        profiles.add(profile)
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("reminder", p.reminder)
                    put("hash", p.hash)
                }
            )
        }
        indexFile.writeText(arr.toString())
    }

    fun loadAll(): List<Profile> {
        if (!indexFile.exists()) return emptyList()
        val text = indexFile.readText()
        if (text.isBlank()) return emptyList()
        val arr = JSONArray(text)
        val result = mutableListOf<Profile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(
                Profile(
                    name = o.getString("name"),
                    reminder = o.getString("reminder"),
                    hash = o.getString("hash")
                )
            )
        }
        return result
    }

    fun loadImageFor(name: String): Bitmap? {
        val file = File(dir, "${'$'}name.png")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}
