package com.example.musicai

import org.json.JSONObject

/** Plan de mix décidé par l'IA (Ollama). Toutes les valeurs sont bornées pour rester sûres. */
data class MixPlan(
    val title: String = "Ma musique IA",
    val bpm: Int = 100,
    val root: String = "A",
    val minor: Boolean = true,
    val chords: List<Int> = listOf(1, 6, 3, 7),
    val bars: Int = 16,
    val drums: String = "four_on_floor",
    val melody: Boolean = true,
    val bassVol: Float = 0.7f,
    val chordsVol: Float = 0.5f,
    val drumsVol: Float = 0.7f,
    val melodyVol: Float = 0.4f,
    val voiceVol: Float = 1.0f,
    val voiceReverb: Float = 0.2f,
    val voiceDelay: Float = 0.0f,
    val voiceStartBar: Float = 0f,
    val voiceLoop: Boolean = false,
    val notes: String = ""
) {
    companion object {
        private val FLATS = mapOf(
            "DB" to "C#", "EB" to "D#", "GB" to "F#", "AB" to "G#", "BB" to "A#"
        )
        private val ROOTS = setOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val DRUMS = setOf("none", "four_on_floor", "hiphop", "breakbeat", "half_time")

        fun fromJson(raw: String): MixPlan {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "Réponse IA sans JSON" }
            val o = JSONObject(raw.substring(start, end + 1))
            val d = MixPlan()

            fun f(k: String, def: Float, lo: Float, hi: Float): Float =
                o.optDouble(k, def.toDouble()).toFloat().let { if (it.isNaN()) def else it }.coerceIn(lo, hi)

            val chArr = o.optJSONArray("chords")
            val chords = if (chArr != null && chArr.length() > 0)
                (0 until chArr.length()).map { chArr.optInt(it, 1).coerceIn(1, 7) }
            else d.chords

            var root = o.optString("root", d.root).trim().uppercase()
            root = FLATS[root] ?: root
            if (root !in ROOTS) root = d.root

            val drums = o.optString("drums", d.drums).trim().lowercase().let { if (it in DRUMS) it else d.drums }

            return MixPlan(
                title = o.optString("title", d.title).ifBlank { d.title },
                bpm = o.optInt("bpm", d.bpm).coerceIn(60, 180),
                root = root,
                minor = !o.optString("scale", "minor").trim().lowercase().startsWith("maj"),
                chords = chords,
                bars = o.optInt("bars", d.bars).coerceIn(8, 64),
                drums = drums,
                melody = o.optBoolean("melody", d.melody),
                bassVol = f("bassVol", d.bassVol, 0f, 1.5f),
                chordsVol = f("chordsVol", d.chordsVol, 0f, 1.5f),
                drumsVol = f("drumsVol", d.drumsVol, 0f, 1.5f),
                melodyVol = f("melodyVol", d.melodyVol, 0f, 1.5f),
                voiceVol = f("voiceVol", d.voiceVol, 0f, 1.5f),
                voiceReverb = f("voiceReverb", d.voiceReverb, 0f, 1f),
                voiceDelay = f("voiceDelay", d.voiceDelay, 0f, 1f),
                voiceStartBar = f("voiceStartBar", d.voiceStartBar, 0f, 8f),
                voiceLoop = o.optBoolean("voiceLoop", d.voiceLoop),
                notes = o.optString("notes", "")
            )
        }
    }
}
