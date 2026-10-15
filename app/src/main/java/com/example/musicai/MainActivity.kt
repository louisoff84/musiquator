package com.example.musicai

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { MusicScreen() }
            }
        }
    }
}

@Composable
private fun MusicScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(OllamaClient.DEFAULT_URL) }
    var model by remember { mutableStateOf(OllamaClient.DEFAULT_MODEL) }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    var voiceUri by remember { mutableStateOf<Uri?>(null) }
    var voiceName by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Prêt.") }
    var plan by remember { mutableStateOf<MixPlan?>(null) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            runCatching { recorder?.release() }
        }
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            voiceUri = uri
            voiceName = displayName(ctx, uri)
            status = "Voix importée : ${voiceName}"
        }
    }

    fun startRecording() {
        try {
            val file = File(ctx.cacheDir, "voice_rec.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(file.path)
            r.prepare()
            r.start()
            recorder = r
            recording = true
            status = "Enregistrement en cours…"
        } catch (e: Exception) {
            status = "Micro indisponible : ${e.message}"
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            voiceUri = Uri.fromFile(File(ctx.cacheDir, "voice_rec.m4a"))
            voiceName = "Enregistrement micro"
            status = "Voix enregistrée."
        } catch (e: Exception) {
            status = "Enregistrement trop court ou invalide."
        } finally {
            runCatching { recorder?.release() }
            recorder = null
            recording = false
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else status = "Permission micro refusée."
    }

    fun generate() {
        scope.launch {
            busy = true
            player?.release(); player = null; playing = false
            try {
                val sr = AudioEngine.SR
                val voice = voiceUri?.let { uri ->
                    status = "Décodage de la voix…"
                    withContext(Dispatchers.Default) { AudioDecoder.decode(ctx, uri, sr) }
                }
                status = "L'IA prépare le plan de mix…"
                val p = withContext(Dispatchers.IO) {
                    OllamaClient.plan(serverUrl.trim(), model.trim(), apiKey, prompt, voice?.let { it.size / sr.toFloat() })
                }
                plan = p
                status = "Rendu audio du mix…"
                val file = withContext(Dispatchers.Default) {
                    val pcm = AudioEngine.render(p, voice)
                    File(ctx.cacheDir, "mix.wav").also { WavWriter.write(it, pcm, sr) }
                }
                resultFile = file
                status = "Terminé : ${p.title}"
            } catch (e: Exception) {
                status = "Erreur : ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun togglePlay() {
        val f = resultFile ?: return
        if (playing) {
            player?.pause(); playing = false
        } else {
            val mp = player ?: MediaPlayer().apply {
                setDataSource(f.path)
                prepare()
                setOnCompletionListener { playing = false }
            }.also { player = it }
            mp.start(); playing = true
        }
    }

    fun export() {
        val f = resultFile ?: return
        val name = (plan?.title ?: "mix").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "mix" }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MusicAI")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("insertion impossible")
            ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            status = "Exporté dans Musique/MusicAI/$name.wav"
        } catch (e: Exception) {
            status = "Export impossible : ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Music AI", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Décris ta musique (style, ambiance, tempo…)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voix", style = MaterialTheme.typography.titleMedium)
                Text(voiceName ?: "Aucune voix (instrumental)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickAudio.launch("audio/*") }, enabled = !busy && !recording) {
                        Text("Importer")
                    }
                    OutlinedButton(
                        onClick = {
                            if (recording) stopRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !busy
                    ) { Text(if (recording) "Arrêter" else "Enregistrer") }
                    if (voiceUri != null) {
                        OutlinedButton(
                            onClick = { voiceUri = null; voiceName = null },
                            enabled = !busy && !recording
                        ) { Text("Retirer") }
                    }
                }
            }
        }

        Button(
            onClick = { generate() },
            enabled = !busy && !recording,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Créer et mixer avec l'IA") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
            Text(status, modifier = Modifier.padding(top = 4.dp))
        }

        plan?.let { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text("${p.bpm} BPM · ${p.root} ${if (p.minor) "mineur" else "majeur"} · ${p.bars} mesures · batterie : ${p.drums}")
                    if (p.notes.isNotBlank()) Text(p.notes)
                }
            }
        }

        if (resultFile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { togglePlay() }) { Text(if (playing) "Pause" else "Écouter") }
                OutlinedButton(onClick = { export() }) { Text("Exporter en WAV") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Serveur Ollama", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = serverUrl, onValueChange = { serverUrl = it },
                    label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Modèle") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("Clé d'API (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun displayName(ctx: Context, uri: Uri): String {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) return c.getString(i)
    }
    return uri.lastPathSegment ?: "audio"
}
