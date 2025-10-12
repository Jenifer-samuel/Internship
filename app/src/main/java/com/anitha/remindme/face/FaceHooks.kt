package com.anitha.remindme.face

import android.content.Context
import android.widget.Toast
import com.anitha.remindme.reminder.ReminderService

fun onFaceRecognized(context: Context, name: String) {
    Toast.makeText(context, "Recognized $name", Toast.LENGTH_SHORT).show()
    ReminderService.requestSpeak(context, "Hey $name, you have pending tasks.")
}
