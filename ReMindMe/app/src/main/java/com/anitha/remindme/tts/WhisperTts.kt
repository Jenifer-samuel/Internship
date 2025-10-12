package com.anitha.remindme.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class WhisperTts(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            // Try to pick a soft/whisper-like voice if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val whisperLike = tts.voices?.firstOrNull { voice: Voice ->
                    val n = voice.name.lowercase(Locale.getDefault())
                    n.contains("whisper") || n.contains("soft") || n.contains("breath")
                }
                if (whisperLike != null) {
                    tts.voice = whisperLike
                } else {
                    tts.setPitch(0.9f)
                    tts.setSpeechRate(0.9f)
                }
            }
        }
    }

    fun whisper(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "remindme-whisper")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
