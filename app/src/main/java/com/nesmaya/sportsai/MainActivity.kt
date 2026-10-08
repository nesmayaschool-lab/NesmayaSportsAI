           package com.nesmaya.sportsai

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private data class Player(
    val id: Long,
    val name: String,
    val shirtNumber: String,
    val imageUri: String?
)

private const val PLAYER_PREFS = "nesmaya_sports_players"
private const val PLAYER_DATA = "players"

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
                showSportsApp()
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
            showSportsApp()
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

    private fun showSportsApp() {
        setContent {
            NesmayaSportsApp()
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
private fun NesmayaSportsApp() {

    var currentPage by remember {
        mutableStateOf("المباراة")
    }

    val context = LocalContext.current

    val players =
        remember {
            mutableStateListOf<Player>().apply {
                addAll(loadPlayers(context))
            }
        }

    MaterialTheme {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {
                        currentPage = "المباراة"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("المباراة")
                }

                Button(
                    onClick = {
                        currentPage = "اللاعبون"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("اللاعبون")
                }
            }

            if (currentPage == "المباراة") {

                MatchScreen()

            } else {

                PlayersScreen(
                    players = players
                )
            }
        }
    }
}

@Composable
private fun MatchScreen() {

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
        mutableStateOf(
            CameraSelector.LENS_FACING_BACK
        )
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }

    var previewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    var stopwatchRunning by remember {
        mutableStateOf(false)
    }

    var elapsedSeconds by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(stopwatchRunning) {

        while (stopwatchRunning) {

            delay(1000)

            elapsedSeconds++
        }
    }

    LaunchedEffect(
        cameraProvider,
        videoCapture,
        lensFacing,
        previewView
    ) {

        val provider =
            cameraProvider
                ?: return@LaunchedEffect

        val capture =
            videoCapture
                ?: return@LaunchedEffect

        val previewSurface =
            previewView
                ?: return@LaunchedEffect

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

        Text(
            text = "نسماية سبورت",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(12.dp)
        )

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

                        val future =
                            ProcessCameraProvider
                                .getInstance(ctx)

                        future.addListener(

                            {

                                cameraProvider =
                                    future.get()

                                val recorder =
                                    Recorder.Builder()
                                        .setQualitySelector(
                                            QualitySelector.from(
                                                Quality.HD
                                            )
                                        )
                                        .build()

                                videoCapture =
                                    VideoCapture.withOutput(
                                        recorder
                                    )
                            },

                            ContextCompat.getMainExecutor(
                                ctx
                            )
                        )
                    }
                }
            )

            Text(
                text = formatMatchTime(
                    elapsedSeconds
                ),
                style =
                    MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                        videoCapture
                            ?: return@Button

                    if (recording == null) {

                        val name =
                            "NesmayaSportsAI_" +
                                    System.currentTimeMillis() +
                                    ".mp4"

                        val values =
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

                        val output =
                            MediaStoreOutputOptions
                                .Builder(
                                    context.contentResolver,
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                )
                                .setContentValues(values)
                                .build()

                        val pending =
                            capture.output
                                .prepareRecording(
                                    context,
                                    output
                                )
                                .withAudioEnabled()

                        recording =
                            pending.start(
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                    elapsedSeconds = 0L
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("تصفير")
            }
        }
    }
}

@Composable
private fun PlayersScreen(
    players: SnapshotStateList<Player>
) {

    val context = LocalContext.current

    var playerName by remember {
        mutableStateOf("")
    }

    var shirtNumber by remember {
        mutableStateOf("")
    }

    var selectedImageUri by remember {
        mutableStateOf<String?>(null)
    }

    var displayMode by remember {
        mutableStateOf("بطاقات")
    }

    val imagePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {

                try {

                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                } catch (_: Exception) {
                }

                selectedImageUri =
                    uri.toString()
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        Text(
            text = "اللاعبون",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = playerName,
            onValueChange = {
                playerName = it
            },
            label = {
                Text("اسم اللاعب")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = shirtNumber,
            onValueChange = {
                shirtNumber = it
            },
            label = {
                Text("رقم القميص - اختياري")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            OutlinedButton(
                onClick = {
                    imagePicker.launch(
                        arrayOf(
                            "image/*"
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("اختيار صورة")
            }

            Button(
                onClick = {

                    if (playerName.trim().isNotEmpty()) {

                        val player =
                            Player(
                                id =
                                    System.currentTimeMillis(),
                                name =
                                    playerName.trim(),
                                shirtNumber =
                                    shirtNumber.trim(),
                                imageUri =
                                    selectedImageUri
                            )

                        players.add(player)

                        savePlayers(
                            context,
                            players
                        )

                        playerName = ""
                        shirtNumber = ""
                        selectedImageUri = null
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("إضافة لاعب")
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * اختيار شكل عرض بيانات اللاعبين.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            Button(
                onClick = {
                    displayMode = "قائمة"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("قائمة")
            }

            Button(
                onClick = {
                    displayMode = "بطاقات"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("بطاقات")
            }

            Button(
                onClick = {
                    displayMode = "جدول"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("جدول")
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /*
         * عرض اللاعبين حسب الاختيار.
         */
        when (displayMode) {

            "قائمة" -> {

                PlayerListView(
                    players = players,
                    onDelete = { player ->

                        players.remove(player)

                        savePlayers(
                            context,
                            players
                        )
                    }
                )
            }

            "بطاقات" -> {

                PlayerCardsView(
                    players = players,
                    onDelete = { player ->

                        players.remove(player)

                        savePlayers(
                            context,
                            players
                        )
                    }
                )
            }

            "جدول" -> {

                PlayerTableView(
                    players = players,
                    onDelete = { player ->

                        players.remove(player)

                        savePlayers(
                            context,
                            players
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PlayerListView(
    players: List<Player>,
    onDelete: (Player) -> Unit
) {

    LazyColumn(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        items(
            players,
            key = { it.id }
        ) { player ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    PlayerAvatar(
                        player = player
                    )

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = player.name,
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text =
                                if (
                                    player.shirtNumber
                                        .isNotEmpty()
                                ) {
                                    "رقم القميص: " +
                                            player.shirtNumber
                                } else {
                                    "بدون رقم"
                                }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDelete(player)
                        }
                    ) {
                        Text("حذف")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCardsView(
    players: List<Player>,
    onDelete: (Player) -> Unit
) {

    LazyColumn(
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        items(
            players,
            key = { it.id }
        ) { player ->

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    PlayerAvatar(
                        player = player,
                        large = true
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = player.name,
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text =
                            if (
                                player.shirtNumber
                                    .isNotEmpty()
                            ) {
                                "رقم ${player.shirtNumber}"
                            } else {
                                "بدون رقم"
                            }
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            onDelete(player)
                        }
                    ) {
                        Text("حذف اللاعب")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTableView(
    players: List<Player>,
    onDelete: (Player) -> Unit
) {

    LazyColumn(
        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {

                    Text(
                        text = "الاسم",
                        modifier = Modifier.weight(2f)
                    )

                    Text(
                        text = "الرقم",
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "إجراء",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        items(
            players,
            key = { it.id }
        ) { player ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = player.name,
                        modifier = Modifier.weight(2f)
                    )

                    Text(
                        text =
                            if (
                                player.shirtNumber
                                    .isNotEmpty()
                            ) {
                                player.shirtNumber
                            } else {
                                "-"
                            },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            onDelete(player)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حذف")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerAvatar(
    player: Player,
    large: Boolean = false
) {

    val context = LocalContext.current

    val size =
        if (large) {
            110.dp
        } else {
            60.dp
        }

    if (player.imageUri == null) {

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary
                ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    player.name
                        .firstOrNull()
                        ?.toString()
                        ?: "?",
                style =
                    if (large) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.titleLarge
                    }
            )
        }

    } else {

        AndroidView(
            modifier = Modifier
                .size(size)
                .clip(CircleShape),

            factory = {

                ImageView(context).apply {

                    scaleType =
                        ImageView.ScaleType.CENTER_CROP

                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                }
            },

            update = { imageView ->

                try {

                    val uri =
                        Uri.parse(
                            player.imageUri
                        )

                    val stream =
                        context.contentResolver
                            .openInputStream(uri)

                    val bitmap =
                        BitmapFactory
                            .decodeStream(stream)

                    stream?.close()

                    imageView.setImageBitmap(bitmap)

                } catch (_: Exception) {

                    imageView.setImageDrawable(null)
                }
            }
        )
    }
}

private fun loadPlayers(
    context: Context
): List<Player> {

    val preferences =
        context.getSharedPreferences(
            PLAYER_PREFS,
            Context.MODE_PRIVATE
        )

    val json =
        preferences.getString(
            PLAYER_DATA,
            null
        )
            ?: return emptyList()

    return try {

        val array =
            JSONArray(json)

        val result =
            mutableListOf<Player>()

        for (i in 0 until array.length()) {

            val item =
                array.getJSONObject(i)

            result.add(
                Player(
                    id =
                        item.getLong("id"),
                    name =
                        item.getString("name"),
                    shirtNumber =
                        item.optString(
                            "shirtNumber",
                            ""
                        ),
                    imageUri =
                        if (
                            item.isNull("imageUri")
                        ) {
                            null
                        } else {
                            item.optString(
                                "imageUri",
                                null
                            )
                        }
                )
            )
        }

        result

    } catch (_: Exception) {

        emptyList()
    }
}

private fun savePlayers(
    context: Context,
    players: List<Player>
) {

    val array =
        JSONArray()

    players.forEach { player ->

        val item =
            JSONObject().apply {

                put(
                    "id",
                    player.id
                )

                put(
                    "name",
                    player.name
                )

                put(
                    "shirtNumber",
                    player.shirtNumber
                )

                if (player.imageUri == null) {

                    put(
                        "imageUri",
                        JSONObject.NULL
                    )

                } else {

                    put(
                        "imageUri",
                        player.imageUri
                    )
                }
            }

        array.put(item)
    }

    context
        .getSharedPreferences(
            PLAYER_PREFS,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            PLAYER_DATA,
            array.toString()
        )
        .apply()
}

private fun formatMatchTime(
    totalSeconds: Long
): String {

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%02d:%02d".format(
        minutes,
        seconds
    )
}
