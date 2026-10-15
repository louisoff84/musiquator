package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OllamaClient {
    const val DEFAULT_URL = "http://82.26.80.20:11434"
    const val DEFAULT_MODEL = "gpt-oss:120b-cloud"

    private val SYSTEM_PROMPT = """
Tu es un producteur de musique et ingénieur du mix. L'utilisateur décrit la musique voulue et fournit parfois une voix (a cappella) à mixer avec une instrumentale générée par synthétiseur.
Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour ni markdown, avec exactement ces clés :
- title (string)
- bpm (entier 60-180)
- root (C, C#, D, D#, E, F, F#, G, G#, A, A#, B)
- scale ("minor" ou "major")
- chords (liste de 4 à 8 entiers 1-7 : degrés de la gamme, une valeur par mesure, répétée en boucle)
- bars (entier 8-64 : nombre de mesures)
- drums ("none", "four_on_floor", "hiphop", "breakbeat" ou "half_time")
- melody (true/false : mélodie synthé en plus)
- bassVol, chordsVol, drumsVol, melodyVol, voiceVol (nombres 0 à 1.5)
- voiceReverb (0 à 1), voiceDelay (0 à 1)
- voiceStartBar (nombre de mesures avant l'entrée de la voix, 0 à 8)
- voiceLoop (true pour répéter la voix, false sinon)
- notes (une phrase qui explique tes choix de mix)
Adapte le tempo, la tonalité, l'harmonie et le mix au style demandé et à la durée de la voix.
""".trimIndent()

    /** Appel bloquant : à lancer hors du thread principal. */
    fun plan(baseUrl: String, model: String, apiKey: String, userPrompt: String, voiceSeconds: Float?): MixPlan {
        val user = buildString {
            append("Demande : ").append(userPrompt.ifBlank { "Une chanson agréable et équilibrée" })
            if (voiceSeconds != null) {
                append("\nUne voix de ").append("%.1f".format(voiceSeconds))
                    .append(" secondes est fournie : fais-la ressortir dans le mix.")
            } else {
                append("\nAucune voix fournie : musique instrumentale.")
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("format", "json")
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", user))
            )

        val conn = URL("${baseUrl.trimEnd('/')}/api/chat").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) MusicAI/1.0"
            )
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = if (text.trimStart().startsWith("<")) {
                    "page HTML renvoyée (proxy, pare-feu ou authentification devant Ollama, pas Ollama lui-même)"
                } else {
                    runCatching { JSONObject(text).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: text.take(200)
                }
                error("HTTP $code : $detail")
            }

            val content = JSONObject(text).getJSONObject("message").getString("content")
            return MixPlan.fromJson(content)
        } finally {
            conn.disconnect()
        }
    }
}
