package com.nesmaya.sportsai

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val cameraGranted =
                permissions[Manifest.permission.CAMERA] == true

            val audioGranted =
                permissions[Manifest.permission.RECORD_AUDIO] == true

            if (cameraGranted && audioGranted) {
                showSportsCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        val audioGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted && audioGranted) {
            showSportsCamera()
        } else {
            setContent {
                PermissionScreen {
                    cameraPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                }
            }
        }
    }

    private fun showSportsCamera() {
        setContent {
            NesmayaSportsCamera()
        }
    }
}

@Composable
private fun PermissionScreen(
    onRequestPermission: () -> Unit
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "نسماية سبورت",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "نحتاج إلى الكاميرا والميكروفون لتشغيل تصوير المباريات وتسجيل الفيديو."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("السماح بالكاميرا والميكروفون")
            }
        }
    }
}

@Composable
private fun NesmayaSportsCamera() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoCapture by remember {
        mutableStateOf<VideoCapture<Recorder>?>(null)
    }

    var recording by remember {
        mutableStateOf<Recording?>(null)
    }

    var isRecording by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "نسماية سبورت",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),

                factory = { ctx ->

                    PreviewView(ctx).also { previewView ->

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({

                            val cameraProvider =
                                cameraProviderFuture.get()

                            val preview =
                                Preview.Builder()
                                    .build()
                                    .also {
                                        it.surfaceProvider =
                                            previewView.surfaceProvider
                                    }

                            val recorder =
                                Recorder.Builder()
                                    .setQualitySelector(
                                        QualitySelector.from(Quality.HD)
                                    )
                                    .build()

                            val newVideoCapture =
                                VideoCapture.withOutput(recorder)

                            videoCapture = newVideoCapture

                            val cameraSelector =
                                CameraSelector.DEFAULT_BACK_CAMERA

                            try {

                                cameraProvider.unbindAll()

                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    newVideoCapture
                                )

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Button(
                onClick = {
                    // زر التصوير سيُفعّل لاحقًا لالتقاط الصور.
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("تصوير")
            }

            Button(
                onClick = {

                    val capture = videoCapture ?: return@Button

                    if (recording == null) {

                        val name =
                            "NesmayaSportsAI_" +
                                    System.currentTimeMillis() +
                                    ".mp4"

                        val contentValues =
                            ContentValues().apply {

                                put(
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    name
                                )

                                put(
                                    MediaStore.Video.Media.MIME_TYPE,
                                    "video/mp4"
                                )

                                put(
                                    MediaStore.Video.Media.RELATIVE_PATH,
                                    "Movies/NesmayaSportsAI"
                                )
                            }

                        val mediaStoreOutput =
                            MediaStoreOutputOptions
                                .Builder(
                                    context.contentResolver,
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                )
                                .setContentValues(contentValues)
                                .build()

                        val pendingRecording =
                            capture.output
                                .prepareRecording(
                                    context,
                                    mediaStoreOutput
                                )
                                .withAudioEnabled()

                        recording =
                            pendingRecording.start(
                                ContextCompat.getMainExecutor(context)
                            ) { event ->

                                when (event) {

                                    is VideoRecordEvent.Start -> {
                                        isRecording = true
                                    }

                                    is VideoRecordEvent.Finalize -> {
                                        isRecording = false
                                        recording = null
                                    }
                                }
                            }

                    } else {

                        recording?.stop()
                        recording = null
                        isRecording = false
                    }
                },

                modifier = Modifier.weight(1f)
            ) {

                Text(
                    if (isRecording) {
                        "إيقاف التسجيل"
                    } else {
                        "تسجيل"
                    }
                )
            }
        }
    }
}