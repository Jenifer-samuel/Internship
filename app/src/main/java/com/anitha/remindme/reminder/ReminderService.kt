package com.anitha.remindme.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.media.AudioManager
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.anitha.remindme.R
import com.anitha.remindme.data.AppDatabase
import com.anitha.remindme.data.TaskEntity
import com.anitha.remindme.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ReminderService : LifecycleService(), TextToSpeech.OnInitListener {

    private lateinit var db: AppDatabase
    private var serviceJob: Job? = null
    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        tts = TextToSpeech(this, this)
        startForeground(NOTIF_ID, buildNotification("ReMindMe is monitoring tasks"))
        startTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_SPEAK)?.let { text ->
            speak(text)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.getDefault()
            val result = tts?.setLanguage(locale)
            tts?.setPitch(0.5f)
            tts?.setSpeechRate(0.7f)
            // Route audio via media stream and respect Bluetooth routing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS language not supported")
            } else {
                ttsReady.set(true)
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    private fun startTicker() {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkDueTasks()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error checking tasks", t)
                }
                delay(60_000)
            }
        }
    }

    private suspend fun checkDueTasks() {
        val pending = db.taskDao().getAllPending()
        if (pending.isEmpty()) return
        val now = nowMinutes()
        val due = pending.filter { parseTimeMinutes(it.time) <= now && !it.completed }
        if (due.isNotEmpty()) {
            val message = buildString {
                append("You have ")
                append(due.size)
                append(" pending tasks. ")
                due.take(3).forEach { append(it.title).append(". ") }
            }
            speak(message)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady.get()) return
        // For legacy devices, ensure music stream; for L+ AudioAttributes above applies
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, System.currentTimeMillis().toString())
    }

    private fun buildNotification(content: String): Notification {
        val channelId = createChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ReMindMe")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel(): String {
        val channelId = "remindme_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_LOW)
            chan.description = "Task reminders"
            chan.setSound(null, null)
            manager.createNotificationChannel(chan)
        }
        return channelId
    }

    companion object {
        private const val TAG = "ReminderService"
        private const val NOTIF_ID = 101
        private const val EXTRA_SPEAK = "extra_speak"

        fun start(context: Context) {
            val intent = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun requestSpeak(context: Context, text: String) {
            val intent = Intent(context, ReminderService::class.java).apply {
                putExtra(EXTRA_SPEAK, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun nowMinutes(): Int {
            val cal = java.util.Calendar.getInstance()
            return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        }

        fun parseTimeMinutes(time: String): Int {
            return try {
                val parts = time.split(":")
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                h * 60 + m
            } catch (e: Exception) {
                Int.MAX_VALUE
            }
        }
    }
}

object BundleCompatHelper {
    fun audioParams(): android.os.Bundle? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                // Route via speaker/Bluetooth handled by system audio routing
            }
        } else null
    }
}
