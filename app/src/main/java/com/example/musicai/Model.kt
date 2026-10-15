package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Générateur d'identifiants stables pour les notes et les pistes (sélection dans l'interface). */
object Ids {
    private var n = 1
    @Synchronized
    fun next(): Int = n++
}

/** Une note éditable : une seule hauteur MIDI, début et durée en temps (noires). */
data class PNote(val id: Int, val pitch: Int, val start: Float, val dur: Float, val vel: Float = 0.8f)

data class PVoice(
    val volume: Float = 1.0f,
    val reverb: Float = 0.2f,
    val delay: Float = 0f,
    val startBar: Float = 0f,
    val loop: Boolean = false,
    val duck: Float = 0.35f
)

data class PTrack(
    val id: Int,
    val name: String,
    val instrument: String,
    val volume: Float = 0.8f,
    val reverb: Float = 0f,
    val delay: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val notes: List<PNote> = emptyList(),
    /** Batterie : son -> motif en doubles-croches (16 caractères par mesure). */
    val steps: Map<String, String> = emptyMap(),
    val hits: List<DrumHit> = emptyList(),
    val fromBar: Int = 0,
    val toBar: Int = 999
) {
    val isDrums: Boolean get() = instrument == "drums"
}

data class Project(
    val id: String,
    val title: String,
    val bpm: Int = 100,
    val bars: Int = 8,
    val tracks: List<PTrack> = emptyList(),
    val voice: PVoice = PVoice(),
    val hasVoice: Boolean = false,
    val voiceName: String = "",
    val lyrics: String = "",
    val aiNotes: String = "",
    val aiAnalysis: String = "",
    val updated: Long = System.currentTimeMillis()
)

data class ProjectMeta(
    val id: String,
    val title: String,
    val bpm: Int,
    val bars: Int,
    val tracks: Int,
    val updated: Long,
    val hasVoice: Boolean
)

val DRUM_NAMES = listOf("kick", "snare", "hat", "openhat", "clap", "tom", "rim")
val DRUM_LABELS = mapOf(
    "kick" to "Grosse caisse", "snare" to "Caisse claire", "hat" to "Charley",
    "openhat" to "Charley ouvert", "clap" to "Clap", "tom" to "Tom", "rim" to "Rim"
)

/** Passe un morceau modifiable au format du synthétiseur. */
fun Project.toSong(): Song {
    val anySolo = tracks.any { it.solo }
    val active = tracks.filter { !it.mute && (!anySolo || it.solo) }
    val out = active.map { t ->
        Track(
            name = t.name,
            instrument = t.instrument,
            volume = t.volume,
            reverb = t.reverb,
            delay = t.delay,
            fromBar = t.fromBar.coerceIn(0, bars),
            toBar = if (t.toBar >= bars) bars else t.toBar.coerceAtLeast(t.fromBar),
            loopBeats = 0f,
            notes = t.notes.map { Note(intArrayOf(it.pitch), it.start, it.dur, it.vel) },
            pattern = t.steps.filterValues { it.length >= 4 },
            hits = t.hits
        )
    }
    return Song(
        title = title,
        bpm = bpm,
        bars = bars,
        tracks = out,
        voice = VoiceSettings(voice.volume, voice.reverb, voice.delay, voice.startBar, voice.loop, voice.duck),
        notes = "",
        json = ""
    )
}

/** Motifs de batterie : même longueur (multiple de 16) pour tous les sons, répétés si besoin. */
fun normSteps(m: Map<String, String>): Map<String, String> {
    val clean = m.filterValues { it.isNotEmpty() }
    if (clean.isEmpty()) return clean
    val maxLen = clean.values.maxOf { it.length }
    val len = (((maxLen + 15) / 16) * 16).coerceIn(16, 64)
    return clean.mapValues { (_, s) ->
        buildString { for (i in 0 until len) append(s[i % s.length]) }
    }
}

object SongImport {
    /** Transforme le morceau de l'IA (motifs bouclés, accords) en pistes éditables note par note. */
    fun tracks(song: Song): List<PTrack> = song.tracks.map { t ->
        val from = t.fromBar * 4.0
        val total = song.bars * 4.0
        val to = if (t.toBar >= song.bars) total else t.toBar * 4.0
        val offsets = ArrayList<Double>()
        if (t.loopBeats >= 1f) {
            var off = from
            while (off < to) { offsets.add(off); off += t.loopBeats }
        } else offsets.add(0.0)

        if (t.instrument == "drums") {
            val hits = ArrayList<DrumHit>()
            for (off in offsets) for (h in t.hits) {
                val s = off + h.start
                if (s < from || s >= to) continue
                hits.add(DrumHit(h.drum, s.toFloat(), h.vel))
            }
            PTrack(
                id = Ids.next(), name = t.name, instrument = "drums",
                volume = t.volume, reverb = t.reverb, delay = t.delay,
                steps = normSteps(t.pattern), hits = hits.take(600),
                fromBar = t.fromBar, toBar = if (t.toBar >= song.bars) 999 else t.toBar
            )
        } else {
            val notes = ArrayList<PNote>()
            for (off in offsets) for (n in t.notes) {
                val s = off + n.start
                if (s < from || s >= to || s >= total) continue
                val d = min(n.dur.toDouble(), to - s).coerceAtLeast(0.05)
                for (p in n.pitches) notes.add(PNote(Ids.next(), p, s.toFloat(), d.toFloat(), n.vel))
            }
            PTrack(
                id = Ids.next(), name = t.name, instrument = t.instrument,
                volume = t.volume, reverb = t.reverb, delay = t.delay,
                notes = notes.take(3000)
            )
        }
    }
}

object ProjectJson {
    private fun d(x: Float) = x.toDouble()

    fun toJson(p: Project): JSONObject = JSONObject().apply {
        put("v", 2)
        put("id", p.id)
        put("title", p.title)
        put("bpm", p.bpm)
        put("bars", p.bars)
        put("updated", p.updated)
        put("hasVoice", p.hasVoice)
        put("voiceName", p.voiceName)
        put("lyrics", p.lyrics)
        put("aiNotes", p.aiNotes)
        put("aiAnalysis", p.aiAnalysis)
        put("voice", JSONObject().apply {
            put("volume", d(p.voice.volume)); put("reverb", d(p.voice.reverb)); put("delay", d(p.voice.delay))
            put("startBar", d(p.voice.startBar)); put("loop", p.voice.loop); put("duck", d(p.voice.duck))
        })
        put("tracks", JSONArray().also { arr -> p.tracks.forEach { arr.put(trackJson(it)) } })
    }

    private fun trackJson(t: PTrack) = JSONObject().apply {
        put("name", t.name)
        put("instrument", t.instrument)
        put("volume", d(t.volume)); put("reverb", d(t.reverb)); put("delay", d(t.delay))
        put("mute", t.mute); put("solo", t.solo)
        put("fromBar", t.fromBar); put("toBar", t.toBar)
        put("notes", JSONArray().also { arr ->
            t.notes.forEach { n ->
                arr.put(JSONArray().put(n.pitch).put(d(n.start)).put(d(n.dur)).put(d(n.vel)))
            }
        })
        put("steps", JSONObject().also { o -> t.steps.forEach { (k, v) -> o.put(k, v) } })
        put("hits", JSONArray().also { arr ->
            t.hits.forEach { h -> arr.put(JSONArray().put(h.drum).put(d(h.start)).put(d(h.vel))) }
        })
    }

    fun fromJson(o: JSONObject): Project {
        val v = o.optJSONObject("voice")
        val dv = PVoice()
        val voice = if (v == null) dv else PVoice(
            volume = v.optDouble("volume", dv.volume.toDouble()).toFloat(),
            reverb = v.optDouble("reverb", dv.reverb.toDouble()).toFloat(),
            delay = v.optDouble("delay", dv.delay.toDouble()).toFloat(),
            startBar = v.optDouble("startBar", 0.0).toFloat(),
            loop = v.optBoolean("loop", false),
            duck = v.optDouble("duck", dv.duck.toDouble()).toFloat()
        )
        val tracks = ArrayList<PTrack>()
        val ta = o.optJSONArray("tracks")
        if (ta != null) for (i in 0 until ta.length()) ta.optJSONObject(i)?.let { tracks.add(parseTrack(it)) }
        return Project(
            id = o.getString("id"),
            title = o.optString("title", "Sans titre"),
            bpm = o.optInt("bpm", 100).coerceIn(40, 220),
            bars = o.optInt("bars", 8).coerceIn(1, 64),
            tracks = tracks,
            voice = voice,
            hasVoice = o.optBoolean("hasVoice", false),
            voiceName = o.optString("voiceName", ""),
            lyrics = o.optString("lyrics", ""),
            aiNotes = o.optString("aiNotes", ""),
            aiAnalysis = o.optString("aiAnalysis", ""),
            updated = o.optLong("updated", System.currentTimeMillis())
        )
    }

    private fun parseTrack(t: JSONObject): PTrack {
        val notes = ArrayList<PNote>()
        t.optJSONArray("notes")?.let { na ->
            for (i in 0 until na.length()) {
                val a = na.optJSONArray(i) ?: continue
                notes.add(
                    PNote(
                        Ids.next(), a.optInt(0, 60).coerceIn(12, 108),
                        a.optDouble(1, 0.0).toFloat(), max(0.05, a.optDouble(2, 1.0)).toFloat(),
                        a.optDouble(3, 0.8).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
        val steps = LinkedHashMap<String, String>()
        t.optJSONObject("steps")?.let { so ->
            for (k in so.keys()) steps[k] = so.optString(k, "")
        }
        val hits = ArrayList<DrumHit>()
        t.optJSONArray("hits")?.let { ha ->
            for (i in 0 until ha.length()) {
                val a = ha.optJSONArray(i) ?: continue
                hits.add(DrumHit(a.optString(0), a.optDouble(1, 0.0).toFloat(), a.optDouble(2, 0.9).toFloat()))
            }
        }
        return PTrack(
            id = Ids.next(),
            name = t.optString("name", "Piste"),
            instrument = t.optString("instrument", "keys"),
            volume = t.optDouble("volume", 0.8).toFloat(),
            reverb = t.optDouble("reverb", 0.0).toFloat(),
            delay = t.optDouble("delay", 0.0).toFloat(),
            mute = t.optBoolean("mute", false),
            solo = t.optBoolean("solo", false),
            notes = notes,
            steps = normSteps(steps),
            hits = hits,
            fromBar = t.optInt("fromBar", 0),
            toBar = t.optInt("toBar", 999)
        )
    }
}
