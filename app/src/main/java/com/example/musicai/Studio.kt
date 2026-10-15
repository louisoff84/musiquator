package com.example.musicai

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cerveau de l'application : projet ouvert, historique, lecture, enregistrement, voix, IA, export. */
class Studio(context: Context) {
    private val app: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store = Store(app)
    val settings = AppSettings(app)

    // ---------- état observé par l'interface ----------
    var projects by mutableStateOf<List<ProjectMeta>>(emptyList())
        private set
    var project by mutableStateOf<Project?>(null)
        private set
    var version by mutableIntStateOf(0)
        private set
    var tab by mutableIntStateOf(0)
    var selTrack by mutableIntStateOf(-1)
    var selNote by mutableIntStateOf(-1)
    var toast by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // ---------- lecture ----------
    var playing by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var playBeat by mutableFloatStateOf(0f)
        private set

    // ---------- prise (enregistrement des notes jouées) ----------
    var recording by mutableStateOf(false)
        private set
    var countIn by mutableIntStateOf(0)
        private set
    var recBeat by mutableFloatStateOf(0f)
        private set
    var listenWhileRec by mutableStateOf(true)
    var recStartBar by mutableIntStateOf(0)

    // ---------- voix ----------
    var voice: FloatArray? = null
        private set
    var voiceWave by mutableStateOf<FloatArray?>(null)
        private set
    var voiceAnalysis by mutableStateOf<VoiceAnalysis?>(null)
        private set
    var voiceRecording by mutableStateOf(false)
        private set

    // ---------- IA ----------
    var aiBusy by mutableStateOf(false)
        private set
    var aiStatus by mutableStateOf("")
        private set

    private val undoStack = ArrayList<Project>()
    private val redoStack = ArrayList<Project>()
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var groupEdit = false

    private var saveJob: Job? = null
    private var toastJob: Job? = null
    private var tickJob: Job? = null
    private var recJob: Job? = null
    private var player: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private val mixFile = File(app.cacheDir, "mix.wav")
    private var renderedVersion = -1
    private var renderedBpm = 100

    init {
        refresh()
    }

    fun say(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(3500)
            toast = null
        }
    }

    // =====================================================================
    // Projets
    // =====================================================================

    fun refresh() {
        projects = store.list()
    }

    private fun newId() = System.currentTimeMillis().toString(36) + (0..99).random()

    fun create(title: String, bpm: Int) {
        val p = Project(id = newId(), title = title.trim().ifBlank { "Nouveau projet" }, bpm = bpm, bars = 8)
        store.save(p)
        openProject(p, null)
    }

    fun open(id: String) {
        val p = store.load(id)
        if (p == null) {
            say("Projet illisible")
            return
        }
        openProject(p, if (p.hasVoice) store.loadVoice(id) else null)
    }

    private fun openProject(p: Project, v: FloatArray?) {
        stop()
        flush()
        undoStack.clear()
        redoStack.clear()
        lastKey = null
        project = p
        voice = v
        voiceWave = v?.let { waveform(it) }
        voiceAnalysis = null
        renderedVersion = -1
        version++
        selTrack = p.tracks.firstOrNull()?.id ?: -1
        selNote = -1
        tab = 0
        updateFlags()
    }

    fun close() {
        stop()
        stopRec()
        flush()
        project = null
        voice = null
        voiceWave = null
        refresh()
    }

    fun delete(id: String) {
        store.delete(id)
        refresh()
    }

    fun duplicate(id: String) {
        val p = store.load(id) ?: return
        val nid = newId()
        store.save(p.copy(id = nid, title = p.title + " (copie)", updated = System.currentTimeMillis()))
        if (p.hasVoice) store.copyVoice(id, nid)
        refresh()
    }

    /** Sauvegarde immédiate (changement d'écran, mise en arrière-plan). */
    fun flush() {
        saveJob?.cancel()
        project?.let { runCatching { store.save(it) } }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700)
            val p = project ?: return@launch
            withContext(Dispatchers.IO) { runCatching { store.save(p) } }
        }
    }

    // =====================================================================
    // Édition avec historique
    // =====================================================================

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun commit(n: Project) {
        project = n.copy(updated = System.currentTimeMillis())
        version++
        updateFlags()
        scheduleSave()
    }

    /** [key] regroupe les changements rapprochés (curseur, glisser) en une seule étape d'annulation. */
    fun edit(key: String? = null, f: (Project) -> Project) {
        val p = project ?: return
        val n = f(p)
        if (n == p) return
        val now = SystemClock.elapsedRealtime()
        val merge = key != null && key == lastKey && now - lastKeyAt < 1500
        if (!merge && !groupEdit) {
            undoStack.add(p)
            if (undoStack.size > 80) undoStack.removeAt(0)
            redoStack.clear()
        }
        lastKey = key
        lastKeyAt = now
        commit(n)
    }

    fun undo() {
        val p = project ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(p)
        lastKey = null
        commit(prev)
        fixSelection()
    }

    fun redo() {
        val p = project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(p)
        lastKey = null
        commit(next)
        fixSelection()
    }

    private fun fixSelection() {
        val p = project ?: return
        if (p.tracks.none { it.id == selTrack }) selTrack = p.tracks.firstOrNull()?.id ?: -1
        selNote = -1
    }

    fun track(id: Int): PTrack? = project?.tracks?.firstOrNull { it.id == id }

    fun rename(title: String) = edit { it.copy(title = title.trim().ifBlank { it.title }) }
    fun setBpm(bpm: Int) = edit("bpm") { it.copy(bpm = bpm.coerceIn(40, 220)) }
    fun setBars(bars: Int) = edit("bars") { it.copy(bars = bars.coerceIn(1, 64)) }
    fun setLyrics(text: String) = edit("lyrics") { it.copy(lyrics = text) }

    // ---------- pistes ----------

    fun addTrack(inst: String): Int {
        var id = -1
        edit { p ->
            val info = instInfo(inst)
            val n = p.tracks.count { it.instrument == inst } + 1
            val t = PTrack(
                id = Ids.next(),
                name = if (n == 1) info.label else "${info.label} $n",
                instrument = inst,
                volume = when (inst) { "drums" -> 0.85f; "bass" -> 0.8f; "pad", "strings", "bell" -> 0.5f; else -> 0.7f },
                reverb = when (inst) { "pad", "strings" -> 0.3f; "bell" -> 0.35f; "keys" -> 0.1f; else -> 0f },
                steps = if (inst == "drums") mapOf("kick" to "x...x...x...x...", "hat" to "..x...x...x...x.") else emptyMap()
            )
            id = t.id
            p.copy(tracks = p.tracks + t)
        }
        if (id >= 0) selTrack = id
        return id
    }

    fun updateTrack(id: Int, key: String? = null, f: (PTrack) -> PTrack) =
        edit(key) { p -> p.copy(tracks = p.tracks.map { if (it.id == id) f(it) else it }) }

    fun deleteTrack(id: Int) {
        edit { p -> p.copy(tracks = p.tracks.filter { it.id != id }) }
        fixSelection()
    }

    fun duplicateTrack(id: Int) {
        var nid = -1
        edit { p ->
            val t = p.tracks.firstOrNull { it.id == id } ?: return@edit p
            val c = t.copy(
                id = Ids.next(), name = t.name + " copie",
                notes = t.notes.map { it.copy(id = Ids.next()) }
            )
            nid = c.id
            val idx = p.tracks.indexOfFirst { it.id == id }
            p.copy(tracks = p.tracks.toMutableList().also { it.add(idx + 1, c) })
        }
        if (nid >= 0) selTrack = nid
    }

    // ---------- notes ----------

    fun addNote(tid: Int, pitch: Int, start: Float, dur: Float, vel: Float = 0.8f): Int {
        val n = PNote(Ids.next(), pitch.coerceIn(12, 108), max(0f, start), max(0.05f, dur), vel)
        updateTrack(tid) { it.copy(notes = it.notes + n) }
        return n.id
    }

    fun updateNote(tid: Int, nid: Int, key: String? = null, f: (PNote) -> PNote) =
        updateTrack(tid, key) { t -> t.copy(notes = t.notes.map { if (it.id == nid) f(it) else it }) }

    fun deleteNote(tid: Int, nid: Int) {
        updateTrack(tid) { t -> t.copy(notes = t.notes.filter { it.id != nid }) }
        if (selNote == nid) selNote = -1
    }

    fun duplicateNote(tid: Int, nid: Int) {
        val t = track(tid) ?: return
        val n = t.notes.firstOrNull { it.id == nid } ?: return
        selNote = addNote(tid, n.pitch, n.start + n.dur, n.dur, n.vel)
    }

    /** Recopie tout le motif à la suite (double sa longueur). */
    fun repeatPattern(tid: Int) {
        val p = project ?: return
        val t = track(tid) ?: return
        if (t.notes.isEmpty()) return
        val end = t.notes.maxOf { it.start + it.dur }
        val span = (kotlin.math.ceil(end / 4.0) * 4.0).toFloat()
        updateTrack(tid) { tr ->
            tr.copy(notes = tr.notes + tr.notes.filter { it.start + span < p.bars * 4 }.map {
                it.copy(id = Ids.next(), start = it.start + span)
            })
        }
    }

    fun clearTrack(tid: Int) {
        updateTrack(tid) { it.copy(notes = emptyList(), hits = emptyList()) }
        selNote = -1
    }

    // ---------- batterie ----------

    fun patternLen(t: PTrack): Int = max(16, t.steps.values.maxOfOrNull { it.length } ?: 16)

    fun setStep(tid: Int, drum: String, idx: Int, c: Char) {
        updateTrack(tid) { t ->
            val len = patternLen(t)
            val arr = (t.steps[drum] ?: "").padEnd(len, '.').toCharArray()
            if (idx in arr.indices) arr[idx] = c
            t.copy(steps = t.steps + (drum to String(arr)))
        }
    }

    fun setPatternBars(tid: Int, bars: Int) {
        updateTrack(tid) { t ->
            val len = bars.coerceIn(1, 4) * 16
            t.copy(steps = t.steps.mapValues { (_, s) ->
                val base = s.ifEmpty { "." }
                buildString { for (i in 0 until len) append(base[i % base.length]) }
            })
        }
    }

    fun applyPreset(tid: Int, pattern: Map<String, String>) {
        updateTrack(tid) { it.copy(steps = pattern, hits = emptyList()) }
    }

    // =====================================================================
    // Lecture
    // =====================================================================

    fun togglePlay() {
        if (playing) stop() else play(0f)
    }

    fun play(fromBeat: Float) {
        if (recording) return
        scope.launch {
            if (!ensureRendered()) return@launch
            startPlayer(fromBeat)
        }
    }

    private suspend fun ensureRendered(): Boolean {
        val p = project ?: return false
        if (renderedVersion == version && mixFile.exists()) return true
        val ver = version
        val song = p.toSong()
        val v = if (p.hasVoice) voice else null
        preparing = true
        return try {
            withContext(Dispatchers.Default) {
                val pcm = AudioEngine.render(song, v)
                WavWriter.write(mixFile, pcm, AudioEngine.SR)
            }
            renderedVersion = ver
            renderedBpm = song.bpm
            true
        } catch (e: Throwable) {
            say("Rendu impossible : ${e.message}")
            false
        } finally {
            preparing = false
        }
    }

    private fun startPlayer(fromBeat: Float) {
        runCatching { player?.release() }
        tickJob?.cancel()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(mixFile.path)
            mp.prepare()
            mp.setOnCompletionListener { stop() }
            val bpm = renderedBpm
            val ms = (fromBeat * 60000f / bpm).toInt().coerceIn(0, max(0, mp.duration - 50))
            mp.seekTo(ms)
            mp.start()
            player = mp
            playing = true
            tickJob = scope.launch {
                while (isActive) {
                    playBeat = runCatching { mp.currentPosition }.getOrDefault(0) * bpm / 60000f
                    delay(33)
                }
            }
        } catch (e: Exception) {
            playing = false
            say("Lecture impossible : ${e.message}")
        }
    }

    fun stop() {
        tickJob?.cancel()
        runCatching { player?.release() }
        player = null
        playing = false
        playBeat = 0f
    }

    // =====================================================================
    // Prise : on joue au clavier / aux pads pendant que le métronome tourne
    // =====================================================================

    private var recLive = false
    private var recT0 = 0L
    private var recStartBeat = 0f
    private var recTrackId = -1
    private var recDrums = false
    private val held = HashMap<Int, Float>()

    fun startRec(drums: Boolean, inst: String) {
        val p0 = project ?: return
        if (recording) return
        stop()
        var tid = if (drums) p0.tracks.firstOrNull { it.isDrums }?.id ?: -1
        else p0.tracks.firstOrNull { it.id == selTrack && !it.isDrums }?.id ?: -1
        if (tid < 0) tid = addTrack(if (drums) "drums" else inst)
        val p = project ?: return
        recTrackId = tid
        recDrums = drums
        undoStack.add(p)
        if (undoStack.size > 80) undoStack.removeAt(0)
        redoStack.clear()
        updateFlags()
        groupEdit = true
        recording = true
        recLive = false
        held.clear()
        val startBar = recStartBar.coerceIn(0, max(0, p.bars - 1))
        recStartBeat = startBar * 4f
        recBeat = recStartBeat

        recJob = scope.launch {
            val spb = 60000.0 / p.bpm
            val listen = listenWhileRec && p.tracks.any { it.notes.isNotEmpty() || it.steps.isNotEmpty() || it.hits.isNotEmpty() || p.hasVoice } &&
                ensureRendered()
            val t0 = SystemClock.elapsedRealtime()
            var k = 0
            while (isActive) {
                val tk = t0 + (k * spb).toLong()
                val wait = tk - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                if (k < 4) countIn = 4 - k
                else if (k == 4) {
                    countIn = 0
                    recT0 = SystemClock.elapsedRealtime()
                    recLive = true
                    if (listen) startPlayer(recStartBeat)
                }
                Preview.click(k % 4 == 0)
                k++
                val next = t0 + (k * spb).toLong()
                while (isActive && SystemClock.elapsedRealtime() < next) {
                    if (recLive) {
                        recBeat = beatNow()
                        if (recBeat >= p.bars * 4f) {
                            stopRec()
                            return@launch
                        }
                    }
                    delay(12)
                }
            }
        }
    }

    private fun beatNow(): Float {
        val bpm = project?.bpm ?: 100
        return recStartBeat + (SystemClock.elapsedRealtime() - recT0) / (60000f / bpm)
    }

    private fun quant(x: Float) = (x * 4f).roundToInt() / 4f

    fun keyDown(key: Int) {
        if (!recording || !recLive) return
        val b = beatNow()
        if (recDrums) {
            val name = DRUM_NAMES.getOrNull(key) ?: return
            val s = max(0f, quant(b))
            updateTrack(recTrackId) { it.copy(hits = it.hits + DrumHit(name, s, 0.9f)) }
        } else held[key] = b
    }

    fun keyUp(key: Int) {
        if (!recording || recDrums) return
        val s = held.remove(key) ?: return
        finishNote(key, s, beatNow())
    }

    private fun finishNote(pitch: Int, s: Float, e: Float) {
        val p = project ?: return
        val qs = max(0f, quant(s))
        val d = max(0.25f, quant(e - s))
        if (qs >= p.bars * 4f) return
        addNote(recTrackId, pitch, qs, d, 0.85f)
    }

    fun stopRec() {
        if (!recording) return
        recJob?.cancel()
        val e = if (recLive) beatNow() else 0f
        if (recLive) held.forEach { (pitch, s) -> finishNote(pitch, s, e) }
        held.clear()
        recording = false
        recLive = false
        groupEdit = false
        countIn = 0
        stop()
    }

    // =====================================================================
    // Voix
    // =====================================================================

    fun importVoice(uri: Uri) {
        val name = displayName(uri)
        attachVoice(uri, name)
    }

    fun startVoiceRecording(): Boolean {
        return try {
            val file = File(app.cacheDir, "voice_rec.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(file.path)
            r.prepare()
            r.start()
            recorder = r
            voiceRecording = true
            true
        } catch (e: Exception) {
            say("Micro indisponible : ${e.message}")
            false
        }
    }

    fun stopVoiceRecording() {
        val r = recorder ?: return
        var ok = true
        try {
            r.stop()
        } catch (e: Exception) {
            ok = false
        } finally {
            runCatching { r.release() }
            recorder = null
            voiceRecording = false
        }
        if (ok) attachVoice(Uri.fromFile(File(app.cacheDir, "voice_rec.m4a")), "Enregistrement micro")
        else say("Enregistrement trop court")
    }

    private fun attachVoice(uri: Uri, name: String) {
        val id = project?.id ?: return
        scope.launch {
            busy = "Lecture de la voix…"
            try {
                val v = withContext(Dispatchers.Default) { AudioDecoder.decode(app, uri, AudioEngine.SR) }
                require(v.size > AudioEngine.SR / 4) { "audio trop court" }
                withContext(Dispatchers.IO) { store.saveVoice(id, v) }
                voice = v
                voiceWave = waveform(v)
                voiceAnalysis = null
                edit { it.copy(hasVoice = true, voiceName = name) }
                say("Voix ajoutée")
            } catch (e: Exception) {
                say("Voix impossible : ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    fun removeVoice() = edit { it.copy(hasVoice = false, voiceName = "") }

    fun setVoice(f: (PVoice) -> PVoice) = edit("voice") { it.copy(voice = f(it.voice)) }

    fun analyzeVoice() {
        val v = voice ?: return
        scope.launch {
            busy = "Analyse de la voix…"
            try {
                voiceAnalysis = withContext(Dispatchers.Default) { VoiceAnalyzer.analyze(v, AudioEngine.SR) }
            } catch (e: Exception) {
                say("Analyse impossible : ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    private fun displayName(uri: Uri): String {
        app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment ?: "audio"
    }

    private fun waveform(s: FloatArray, n: Int = 220): FloatArray {
        val out = FloatArray(n)
        val step = max(1, s.size / n)
        var mx = 0f
        for (i in 0 until n) {
            var m = 0f
            val a = i * step
            val b = min(s.size, a + step)
            for (j in a until b) m = max(m, abs(s[j]))
            out[i] = m
            mx = max(mx, m)
        }
        if (mx > 0f) for (i in out.indices) out[i] /= mx
        return out
    }

    // =====================================================================
    // IA
    // =====================================================================

    fun runAi(request: String, modify: Boolean) {
        val p0 = project ?: return
        if (aiBusy) return
        scope.launch {
            aiBusy = true
            try {
                val v = if (p0.hasVoice) voice else null
                var analysis = voiceAnalysis
                if (v != null && analysis == null) {
                    aiStatus = "Analyse de la voix…"
                    analysis = withContext(Dispatchers.Default) { VoiceAnalyzer.analyze(v, AudioEngine.SR) }
                    voiceAnalysis = analysis
                }
                aiStatus = if (modify) "L'IA modifie ton morceau…" else "L'IA compose les pistes…"
                val cur = project ?: p0
                val song = withContext(Dispatchers.IO) {
                    Ai.run(
                        settings.url, settings.model, settings.apiKey,
                        if (modify) cur else null, request, cur.lyrics, analysis
                    )
                }
                val tracks = SongImport.tracks(song)
                edit { p ->
                    p.copy(
                        title = if (p.title.startsWith("Nouveau") || p.tracks.isEmpty()) song.title else p.title,
                        bpm = song.bpm,
                        bars = song.bars,
                        tracks = tracks,
                        voice = if (p.hasVoice) PVoice(
                            song.voice.volume, song.voice.reverb, song.voice.delay,
                            song.voice.startBar, song.voice.loop, song.voice.duck
                        ) else p.voice,
                        aiNotes = song.notes,
                        aiAnalysis = song.analysis
                    )
                }
                selTrack = tracks.firstOrNull()?.id ?: -1
                selNote = -1
                aiStatus = "Terminé : ${song.title}"
                say("Morceau mis à jour par l'IA (↶ pour annuler)")
            } catch (e: Exception) {
                aiStatus = "Erreur : ${e.message}"
            } finally {
                aiBusy = false
            }
        }
    }

    // =====================================================================
    // Export
    // =====================================================================

    fun export(share: Boolean) {
        val p = project ?: return
        scope.launch {
            busy = "Rendu du morceau…"
            try {
                if (!ensureRendered()) return@launch
                val name = p.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "morceau" }
                val uri = withContext(Dispatchers.IO) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Musiquator")
                    }
                    val u = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("insertion impossible")
                    app.contentResolver.openOutputStream(u)?.use { out -> mixFile.inputStream().use { it.copyTo(out) } }
                    u
                }
                say("Exporté dans Musique/Musiquator/$name.wav")
                if (share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/x-wav"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    app.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (e: Exception) {
                say("Export impossible : ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    fun release() {
        stop()
        stopRec()
        runCatching { recorder?.release() }
        flush()
    }
}
