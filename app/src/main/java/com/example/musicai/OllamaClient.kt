package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OllamaClient {
    const val DEFAULT_URL = "http://82.26.80.20:11434"
    const val DEFAULT_MODEL = "gpt-oss:120b-cloud"

    private val SYSTEM_PROMPT = """
Tu es un compositeur et producteur de musique. Tu composes de vraies pistes audio sous forme de données JSON, jouées ensuite par le synthétiseur de l'application.
Mesure : toujours 4/4. Le temps est compté en noires (beats) : une mesure = 4 temps, une croche = 0.5, une double-croche = 0.25.

Réponds UNIQUEMENT avec un objet JSON valide : pas de markdown, pas de commentaire, pas de texte autour.

STRUCTURE
- title : nom du morceau
- bpm : 50 à 200
- bars : durée totale en mesures (8 à 32)
- tracks : 4 à 8 pistes (voir ci-dessous)
- voice : réglages de la voix de l'utilisateur, s'il y en a une : volume (0-1.5), reverb (0-1), delay (0-1), startBar (mesure d'entrée de la voix), loop (true pour la répéter), duck (0-0.8, baisse de la musique quand la voix chante)
- analysis : ton analyse des paroles et de la voix, objet {"theme":"…","mood":"…","language":"…","structure":["intro","couplet","refrain"],"keywords":["…"],"summary":"2 ou 3 phrases"}
- notes : une phrase qui explique tes choix

PISTE D'INSTRUMENT
{"name":"Basse","instrument":"bass","volume":0.8,"reverb":0,"delay":0,"fromBar":0,"toBar":16,"loopBeats":16,"notes":[[45,0,1,0.9],[45,1.5,0.5,0.7],[["A3","C4","E4"],0,4,0.6]]}
- instrument : bass, sub, pad, strings, lead, pluck, keys, bell
- notes : liste de [hauteur, début, durée, vélocité 0-1]. La hauteur est un numéro MIDI (60 = Do4) ou un nom ("A2", "C#4", "Bb3"). Pour un accord, la hauteur est une liste : [["A3","C4","E4"],0,4,0.6].
- début et durée sont en temps (noires), comptés depuis le début du motif.
- loopBeats : longueur du motif en temps (16 = 4 mesures). Le motif est répété automatiquement de fromBar à toBar. Utilise-le pour garder un JSON court. Sans loopBeats, les débuts sont comptés depuis le début du morceau.
- fromBar / toBar : mesures d'entrée et de sortie de la piste, pour l'arrangement (intro, couplet, refrain, outro).
- volume 0-1.5, reverb 0-1, delay 0-1 (écho en croche pointée).

PISTE DE BATTERIE
{"name":"Batterie","instrument":"drums","volume":0.9,"fromBar":0,"toBar":16,"pattern":{"kick":"x...x...x...x...","snare":"....x.......x...","hat":"..x...x...x...x."}}
- chaque caractère d'un motif est une double-croche : x = coup, X = coup fort, . = silence. Le motif est répété automatiquement (16 caractères = 1 mesure, 32 = 2 mesures).
- sons : kick, snare, hat, openhat, clap, tom, rim.
- optionnel : "hits":[["tom",15,0.8],["clap",31,1]] pour des fills ponctuels (début en temps).

CONSIGNES
- Choisis une tonalité et une progression d'accords adaptées au style demandé, et reste dans la gamme.
- La basse suit les fondamentaux des accords. Ajoute une mélodie mémorable et un pad ou des accords.
- La batterie doit être typique du style (rap : tempo 80-100, kick/snare syncopés, hats en double-croches ; house : 4 sur le sol ; etc.).
- Varie l'arrangement avec fromBar/toBar : intro plus légère, refrain plus chargé, fin allégée.
- Garde le JSON compact : utilise loopBeats plutôt que d'écrire tous les temps.
- Si des paroles sont fournies, analyse-les en profondeur : thème, émotion, langue, structure (couplets, refrain, pont), rimes, nombre de syllabes par ligne, mots-clés. Compose pour les servir : ambiance, harmonie, énergie qui évolue avec le texte. Résume ton analyse dans la clé analysis.
- Si une analyse audio de la voix est fournie (mesurée sur l'appareil), utilise-la : tempoBpm, key (root + mode), pitchRange, syllablesPerSecond, loudnessDb, et vocalSegmentsSec = les passages chantés, en secondes depuis le début du fichier voix. Compose dans la même tonalité que la voix. Prends un bpm égal à tempoBpm (ou sa moitié / son double) quand tempoConfidence est au moins 0.3, sinon choisis librement. Allège la musique pendant les passages chantés et enrichis-la entre eux (intro, pont, outro).
- Si une voix est fournie, laisse de la place dans les médiums, règle voice.startBar pour qu'elle entre au bon moment (laisse une intro musicale) et choisis bpm et bars pour que le morceau dure au moins la durée de la voix + 8 secondes (durée en secondes = bars * 4 * 60 / bpm). Règle voice.volume, reverb, delay et duck selon le style et le volume mesuré.
""".trimIndent()

    /** Appel bloquant : à lancer hors du thread principal. Une seule relance si le JSON est invalide. */
    fun compose(
        baseUrl: String,
        model: String,
        apiKey: String,
        userPrompt: String,
        analysis: VoiceAnalysis?,
        lyrics: String
    ): Song {
        val user = buildString {
            append("Compose ce morceau : ").append(userPrompt.ifBlank { "Une chanson agréable et équilibrée" })
            if (analysis != null) {
                append("\n\nUne voix est fournie. Analyse audio mesurée sur l'appareil :\n")
                    .append(analysis.toJson().toString())
            } else {
                append("\n\nAucune voix fournie : morceau instrumental.")
            }
            if (lyrics.isNotBlank()) {
                append("\n\nParoles :\n").append(lyrics.trim().take(4000))
            }
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", user))

        var lastError = ""
        for (attempt in 1..2) {
            val content = chat(baseUrl, model, apiKey, messages)
            try {
                return SongParser.parse(content)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                messages
                    .put(JSONObject().put("role", "assistant").put("content", content))
                    .put(
                        JSONObject().put("role", "user").put(
                            "content",
                            "Ton JSON est invalide ($lastError). Renvoie uniquement le JSON complet corrigé."
                        )
                    )
            }
        }
        error("JSON de l'IA invalide : $lastError")
    }

    private fun chat(baseUrl: String, model: String, apiKey: String, messages: JSONArray): String {
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("format", "json")
            .put("options", JSONObject().put("temperature", 0.8).put("num_predict", 12000))
            .put("messages", messages)

        val conn = URL("${baseUrl.trimEnd('/')}/api/chat").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 300_000
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
            return JSONObject(text).getJSONObject("message").optString("content", "")
        } finally {
            conn.disconnect()
        }
    }
}
