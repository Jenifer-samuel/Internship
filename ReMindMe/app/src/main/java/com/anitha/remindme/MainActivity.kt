package com.anitha.remindme

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.anitha.remindme.camera.CameraPreview
import com.anitha.remindme.recognition.FaceRecognitionManager
import com.anitha.remindme.tts.WhisperTts
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val recognitionManager by lazy { FaceRecognitionManager(this) }
    private val whisperTts by lazy { WhisperTts(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ))

        setContent {
            MaterialTheme {
                AppScreen(recognitionManager, whisperTts)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        whisperTts.shutdown()
    }
}

@Composable
fun AppScreen(recognitionManager: FaceRecognitionManager, whisperTts: WhisperTts) {
    var name by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var recognizing by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "ReMindMe", style = MaterialTheme.typography.headlineMedium)
        CameraPreview(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onFacesDetected = { faces, bitmapProvider ->
                if (recognizing) return@CameraPreview
                recognizing = true
                val scope = (lifecycleOwner as? ComponentActivity)?.lifecycleScope
                scope?.launch {
                    val bmp = bitmapProvider()
                    if (bmp != null) {
                        recognitionManager.setLatestFaceBitmap(bmp)
                    }
                    val match = recognitionManager.recognize(bmp)
                    recognizing = false
                    if (match != null) {
                        whisperTts.whisper("${'$'}{match.name}, ${'$'}{match.reminder}")
                    }
                }
            }
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true
        )
        OutlinedTextField(
            value = reminder,
            onValueChange = { reminder = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reminder") },
            singleLine = true
        )
        Button(
            onClick = {
                recognitionManager.captureAndEnrollCurrentFace(name, reminder)
            },
            enabled = name.isNotBlank() && reminder.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) { Text("Enroll") }
    }
}
