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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "نحتاج إلى الكاميرا والميكروفون لتشغيل تصوير المباريات وتسجيل الفيديو."
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

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

    var lensFacing by remember {
        mutableStateOf(CameraSelector.LENS_FACING_BACK)
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }

    var previewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    // توقيت المباراة
    var stopwatchRunning by remember {
        mutableStateOf(false)
    }

    var elapsedSeconds by remember {
        mutableLongStateOf(0L)
    }

    // طريقة عرض البيانات
    var displayMode by remember {
        mutableStateOf("بطاقات")
    }

    /*
     * تشغيل توقيت المباراة.
     *
     * التوقيت مستقل عن التسجيل.
     */
    LaunchedEffect(stopwatchRunning) {

        while (stopwatchRunning) {

            delay(1000)

            elapsedSeconds++
        }
    }

    /*
     * ربط الكاميرا.
     *
     * يتم استدعاء هذا الجزء عند:
     * - تشغيل الكاميرا
     * - تغيير الكاميرا الأمامية/الخلفية
     */
    LaunchedEffect(
        cameraProvider,
        videoCapture,
        lensFacing,
        previewView
    ) {

        val provider = cameraProvider ?: return@LaunchedEffect
        val capture = videoCapture ?: return@LaunchedEffect
        val previewSurface = previewView ?: return@LaunchedEffect

        val preview =
            Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider =
                        previewSurface.surfaceProvider
                }

        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

        try {

            provider.unbindAll()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * عنوان التطبيق
         */
        Text(
            text = "نسماية سبورت",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        /*
         * منطقة الكاميرا
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),

                factory = { ctx ->

                    PreviewView(ctx).also { view ->

                        previewView = view

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener(

                            {

                                val provider =
                                    cameraProviderFuture.get()

                                cameraProvider =
                                    provider

                                val recorder =
                                    Recorder.Builder()
                                        .setQualitySelector(
                                            QualitySelector.from(
                                                Quality.HD
                                            )
                                        )
                                        .build()

                                val newVideoCapture =
                                    VideoCapture.withOutput(
                                        recorder
                                    )

                                videoCapture =
                                    newVideoCapture
                            },

                            ContextCompat.getMainExecutor(ctx)
                        )
                    }
                }
            )

            /*
             * عرض توقيت المباراة فوق الكاميرا
             */
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {

                Text(
                    text = formatMatchTime(
                        elapsedSeconds
                    ),
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }

        /*
         * أزرار التحكم في الكاميرا
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {

                    if (!isRecording) {

                        lensFacing =
                            if (
                                lensFacing ==
                                CameraSelector.LENS_FACING_BACK
                            ) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                    }
                },
                enabled = !isRecording,
                modifier = Modifier.weight(1f)
            ) {

                Text("تبديل الكاميرا")
            }

            Button(
                onClick = {

                    val capture =
                        videoCapture ?: return@Button

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
                                .setContentValues(
                                    contentValues
                                )
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
                                ContextCompat.getMainExecutor(
                                    context
                                )
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

        /*
         * التحكم في توقيت المباراة
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {

                    stopwatchRunning = true

                },
                enabled = !stopwatchRunning,
                modifier = Modifier.weight(1f)
            ) {

                Text("بدء")
            }

            Button(
                onClick = {

                    stopwatchRunning = false

                },
                enabled = stopwatchRunning,
                modifier = Modifier.weight(1f)
            ) {

                Text("إيقاف مؤقت")
            }

            Button(
                onClick = {

                    stopwatchRunning = false
                   
