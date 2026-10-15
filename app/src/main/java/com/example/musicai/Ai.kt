package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToLong

/** Pont entre le projet édité à la main et l'IA : le projet part en JSON, l'IA renvoie le morceau complet. */
object Ai {
    private fun r(x: Float): Double = (x * 1000.0).roundToLong() / 1000.0

    /** Le projet au format attendu par l'IA (notes absolues, accords regroupés). */
    fun projectJson(p: Project): String {
        val tracks = JSONArray()
        for (t in p.tracks) {
            val o = JSONObject()
                .put("name", t.name)
                .put("instrument", t.instrument)
                .put("volume", r(t.volume))
                .put("reverb", r(t.reverb))
                .put("delay", r(t.delay))
            if (t.isDrums) {
                o.put("fromBar", t.fromBar)
                o.put("toBar", min(t.toBar, p.bars))
                o.put("pattern", JSONObject().also { po -> t.steps.forEach { (k, v) -> po.put(k, v) } })
                o.put("hits", JSONArray().also { ha ->
                    t.hits.forEach { h -> ha.put(JSONArray().put(h.drum).put(r(h.start)).put(r(h.vel))) }
                })
            } else {
                o.put("fromBar", 0)
                o.put("toBar", p.bars)
                val na = JSONArray()
                t.notes.sortedBy { it.start }
                    .groupBy { Pair(it.start, it.dur) }
                    .forEach { (k, group) ->
                        val pitch: Any = if (group.size == 1) group[0].pitch
                        else JSONArray().also { pa -> group.forEach { pa.put(it.pitch) } }
                        na.put(JSONArray().put(pitch).put(r(k.first)).put(r(k.second)).put(r(group[0].vel)))
                    }
                o.put("notes", na)
            }
            tracks.put(o)
        }
        val root = JSONObject()
            .put("title", p.title)
            .put("bpm", p.bpm)
            .put("bars", p.bars)
            .put("tracks", tracks)
        if (p.hasVoice) {
            root.put(
                "voice", JSONObject()
                    .put("volume", r(p.voice.volume)).put("reverb", r(p.voice.reverb))
                    .put("delay", r(p.voice.delay)).put("startBar", r(p.voice.startBar))
                    .put("loop", p.voice.loop).put("duck", r(p.voice.duck))
            )
        }
        return root.toString()
    }

    /**
     * Appel bloquant (hors thread principal).
     * [current] = morceau actuel à modifier, ou null pour une composition neuve.
     */
    fun run(
        baseUrl: String,
        model: String,
        apiKey: String,
        current: Project?,
        request: String,
        lyrics: String,
        analysis: VoiceAnalysis?
    ): Song {
        val user = buildString {
            if (current != null && current.tracks.isNotEmpty()) {
                append("Voici le morceau actuel de l'utilisateur (JSON). Les notes sont écrites une par une depuis le début du morceau, sans loopBeats :\n")
                append(projectJson(current))
                append("\n\nDemande de l'utilisateur : ")
                append(request.ifBlank { "Améliore ce morceau." })
                append("\n\nRenvoie le morceau COMPLET mis à jour, au même format. Garde à l'identique tout ce que la demande ne concerne pas ")
                append("(mêmes pistes, mêmes notes, mêmes réglages). Pour les nouvelles pistes tu peux utiliser loopBeats.")
            } else {
                append("Compose ce morceau : ").append(request.ifBlank { "Une chanson agréable et équilibrée" })
            }
            if (analysis != null) {
                append("\n\nUne voix est fournie. Analyse audio mesurée sur l'appareil :\n")
                append(analysis.toJson().toString())
            } else if (current == null || !current.hasVoice) {
                append("\n\nAucune voix fournie : morceau instrumental.")
            }
            if (lyrics.isNotBlank()) append("\n\nParoles :\n").append(lyrics.trim().take(4000))
        }
        return OllamaClient.ask(baseUrl, model, apiKey, user)
    }
}
