package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

/** Une note : une ou plusieurs hauteurs MIDI (accord), début et durée en temps (noires). */
class Note(val pitches: IntArray, val start: Float, val dur: Float, val vel: Float)

/** Un coup de batterie isolé (fills). */
class DrumHit(val drum: String, val start: Float, val vel: Float)

class Track(
    val name: String,
    val instrument: String,
    val volume: Float,
    val reverb: Float,
    val delay: Float,
    val fromBar: Int,
    val toBar: Int,
    val loopBeats: Float,              // 0 = pas de répétition
    val notes: List<Note>,
    val pattern: Map<String, String>,  // batterie : son -> motif en doubles-croches
    val hits: List<DrumHit>
)

class VoiceSettings(
    val volume: Float = 1.0f,
    val reverb: Float = 0.2f,
    val delay: Float = 0.0f,
    val startBar: Float = 0f,
    val loop: Boolean = false,
    val duck: Float = 0.35f
)

/** Morceau complet composé par l'IA, décrit en JSON. */
class Song(
    val title: String,
    val bpm: Int,
    val bars: Int,
    val tracks: List<Track>,
    val voice: VoiceSettings,
    val notes: String,
    val json: String               // JSON d'origine (mis en forme) pour affichage / copie
)

object SongParser {
    private val INSTRUMENTS = setOf("bass", "sub", "pad", "strings", "lead", "pluck", "keys", "bell", "drums")

    private val INSTRUMENT_ALIASES = mapOf(
        "drum" to "drums", "drumkit" to "drums", "percussion" to "drums", "perc" to "drums", "beat" to "drums",
        "synth" to "lead", "saw" to "lead", "square" to "lead", "melody" to "lead", "arp" to "pluck",
        "piano" to "keys", "keyboard" to "keys", "rhodes" to "keys", "organ" to "keys", "ep" to "keys",
        "string" to "strings", "violin" to "strings", "cello" to "strings",
        "808" to "sub", "bassline" to "bass", "chords" to "pad", "chord" to "pad",
        "bells" to "bell", "glock" to "bell", "marimba" to "pluck", "guitar" to "pluck"
    )

    private val DRUM_ALIASES = mapOf(
        "kick" to "kick", "bd" to "kick", "bassdrum" to "kick", "k" to "kick",
        "snare" to "snare", "sd" to "snare", "s" to "snare",
        "hat" to "hat", "hihat" to "hat", "hh" to "hat", "closedhat" to "hat", "h" to "hat",
        "openhat" to "openhat", "oh" to "openhat", "open" to "openhat",
        "clap" to "clap", "tom" to "tom", "rim" to "rim", "rimshot" to "rim"
    )

    fun parse(raw: String): Song {
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        require(s >= 0 && e > s) { "pas de JSON dans la réponse" }
        val o = JSONObject(raw.substring(s, e + 1))

        val bars = o.optInt("bars", 16).coerceIn(4, 64)
        val arr = o.optJSONArray("tracks") ?: throw IllegalArgumentException("clé \"tracks\" manquante")
        val tracks = (0 until min(arr.length(), 12)).mapNotNull { i ->
            arr.optJSONObject(i)?.let { parseTrack(it, bars) }
        }
        require(tracks.isNotEmpty()) { "aucune piste valide dans \"tracks\"" }

        val v = o.optJSONObject("voice")
        val d = VoiceSettings()
        val voice = if (v == null) d else VoiceSettings(
            volume = f(v, "volume", d.volume, 0f, 1.5f),
            reverb = f(v, "reverb", d.reverb, 0f, 1f),
            delay = f(v, "delay", d.delay, 0f, 1f),
            startBar = f(v, "startBar", d.startBar, 0f, 16f),
            loop = v.optBoolean("loop", d.loop),
            duck = f(v, "duck", d.duck, 0f, 0.8f)
        )

        return Song(
            title = o.optString("title", "Ma musique IA").ifBlank { "Ma musique IA" },
            bpm = o.optInt("bpm", 100).coerceIn(50, 200),
            bars = bars,
            tracks = tracks,
            voice = voice,
            notes = o.optString("notes", ""),
            json = o.toString(2)
        )
    }

    private fun f(o: JSONObject, k: String, def: Float, lo: Float, hi: Float): Float {
        val x = o.optDouble(k, def.toDouble())
        return (if (x.isNaN()) def else x.toFloat()).coerceIn(lo, hi)
    }

    private fun parseTrack(t: JSONObject, songBars: Int): Track? {
        var inst = t.optString("instrument", "keys").trim().lowercase()
        inst = INSTRUMENT_ALIASES[inst] ?: inst
        if (inst !in INSTRUMENTS) inst = "keys"

        val fromBar = t.optInt("fromBar", 0).coerceIn(0, songBars)
        val toBar = t.optInt("toBar", songBars).coerceIn(fromBar, songBars)
        val loopBeats = f(t, "loopBeats", 0f, 0f, 256f)

        val notes = ArrayList<Note>()
        t.optJSONArray("notes")?.let { na ->
            for (i in 0 until min(na.length(), 800)) {
                parseNote(na.opt(i))?.let { notes.add(it) }
            }
        }

        val pattern = LinkedHashMap<String, String>()
        t.optJSONObject("pattern")?.let { p ->
            for (key in p.keys()) {
                val drum = DRUM_ALIASES[key.trim().lowercase()] ?: continue
                val steps = p.optString(key, "")
                if (steps.isNotEmpty()) pattern[drum] = steps.take(128)
            }
        }

        val hits = ArrayList<DrumHit>()
        t.optJSONArray("hits")?.let { ha ->
            for (i in 0 until min(ha.length(), 400)) {
                val h = ha.opt(i)
                val name: String
                val start: Double
                val vel: Double
                when (h) {
                    is JSONArray -> {
                        name = h.optString(0)
                        start = h.optDouble(1, Double.NaN)
                        vel = h.optDouble(2, 0.9)
                    }
                    is JSONObject -> {
                        name = h.optString("drum", h.optString("d"))
                        start = h.optDouble("start", h.optDouble("s", Double.NaN))
                        vel = h.optDouble("vel", h.optDouble("v", 0.9))
                    }
                    else -> continue
                }
                val drum = DRUM_ALIASES[name.trim().lowercase()] ?: continue
                if (start.isNaN()) continue
                hits.add(DrumHit(drum, start.toFloat().coerceAtLeast(0f), vel.toFloat().coerceIn(0f, 1f)))
            }
        }

        if (inst == "drums") {
            if (pattern.isEmpty() && hits.isEmpty()) return null
        } else if (notes.isEmpty()) return null

        return Track(
            name = t.optString("name", inst).ifBlank { inst },
            instrument = inst,
            volume = f(t, "volume", 0.7f, 0f, 1.5f),
            reverb = f(t, "reverb", 0f, 0f, 1f),
            delay = f(t, "delay", 0f, 0f, 1f),
            fromBar = fromBar,
            toBar = toBar,
            loopBeats = loopBeats,
            notes = notes,
            pattern = pattern,
            hits = hits
        )
    }

    /** Note = [hauteur, début, durée, vélocité] ou {"p":..,"s":..,"d":..,"v":..}. Hauteur = MIDI, nom ("C#4") ou liste (accord). */
    private fun parseNote(item: Any?): Note? {
        val pitchRaw: Any?
        val start: Double
        val dur: Double
        val vel: Double
        when (item) {
            is JSONArray -> {
                pitchRaw = item.opt(0)
                start = item.optDouble(1, Double.NaN)
                dur = item.optDouble(2, 1.0)
                vel = item.optDouble(3, 0.8)
            }
            is JSONObject -> {
                pitchRaw = item.opt("p") ?: item.opt("pitch")
                start = item.optDouble("s", item.optDouble("start", Double.NaN))
                dur = item.optDouble("d", item.optDouble("dur", 1.0))
                vel = item.optDouble("v", item.optDouble("vel", 0.8))
            }
            else -> return null
        }
        if (start.isNaN()) return null
        val pitches = parsePitches(pitchRaw)
        if (pitches.isEmpty()) return null
        return Note(
            pitches = pitches,
            start = start.toFloat().coerceAtLeast(0f),
            dur = (if (dur.isNaN()) 1.0 else dur).toFloat().coerceIn(0.05f, 64f),
            vel = (if (vel.isNaN()) 0.8 else vel).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun parsePitches(x: Any?): IntArray = when (x) {
        is Number -> intArrayOf(x.toInt().coerceIn(12, 108))
        is String -> parsePitchName(x)?.let { intArrayOf(it) } ?: intArrayOf()
        is JSONArray -> (0 until min(x.length(), 8)).flatMap { parsePitches(x.opt(it)).toList() }.toIntArray()
        else -> intArrayOf()
    }

    private val NAME_RE = Regex("^([A-Ga-g])([#b]?)(-?\\d)$")
    private val SEMITONES = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    private fun parsePitchName(s: String): Int? {
        val t = s.trim()
        t.toIntOrNull()?.let { return it.coerceIn(12, 108) }
        val m = NAME_RE.matchEntire(t) ?: return null
        val base = SEMITONES[m.groupValues[1][0].uppercaseChar()] ?: return null
        val acc = when (m.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
        val octave = m.groupValues[3].toInt()
        return ((octave + 1) * 12 + base + acc).coerceIn(12, 108)
    }
}
