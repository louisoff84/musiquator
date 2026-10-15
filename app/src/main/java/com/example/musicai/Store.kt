package com.example.musicai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Projets sur disque : un JSON par projet + la voix en PCM 16 bits mono 44,1 kHz. */
class Store(ctx: Context) {
    private val dir = File(ctx.filesDir, "projects").apply { mkdirs() }

    private fun json(id: String) = File(dir, "$id.json")
    private fun pcm(id: String) = File(dir, "$id.pcm")

    fun list(): List<ProjectMeta> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    ProjectMeta(
                        id = o.getString("id"),
                        title = o.optString("title", "Sans titre"),
                        bpm = o.optInt("bpm", 100),
                        bars = o.optInt("bars", 8),
                        tracks = o.optJSONArray("tracks")?.length() ?: 0,
                        updated = o.optLong("updated", f.lastModified()),
                        hasVoice = o.optBoolean("hasVoice", false)
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.updated }

    fun load(id: String): Project? =
        runCatching { ProjectJson.fromJson(JSONObject(json(id).readText())) }.getOrNull()

    fun save(p: Project) {
        val tmp = File(dir, "${p.id}.json.tmp")
        tmp.writeText(ProjectJson.toJson(p).toString())
        if (!tmp.renameTo(json(p.id))) {
            json(p.id).writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun delete(id: String) {
        json(id).delete()
        pcm(id).delete()
    }

    fun saveVoice(id: String, samples: FloatArray) {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (x in samples) bb.putShort((x.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        pcm(id).writeBytes(bb.array())
    }

    fun loadVoice(id: String): FloatArray? {
        val f = pcm(id)
        if (!f.exists()) return null
        return runCatching {
            val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(bb.remaining() / 2) { bb.short / 32768f }
        }.getOrNull()
    }

    fun copyVoice(from: String, to: String) {
        val f = pcm(from)
        if (f.exists()) f.copyTo(pcm(to), overwrite = true)
    }
}

/** Réglages du serveur Ollama, mémorisés sur l'appareil. */
class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var url by mutableStateOf(sp.getString("url", OllamaClient.DEFAULT_URL) ?: OllamaClient.DEFAULT_URL)
        private set
    var model by mutableStateOf(sp.getString("model", OllamaClient.DEFAULT_MODEL) ?: OllamaClient.DEFAULT_MODEL)
        private set
    var apiKey by mutableStateOf(sp.getString("key", "") ?: "")
        private set

    fun update(newUrl: String, newModel: String, newKey: String) {
        url = newUrl.trim().ifBlank { OllamaClient.DEFAULT_URL }
        model = newModel.trim().ifBlank { OllamaClient.DEFAULT_MODEL }
        apiKey = newKey.trim()
        sp.edit().putString("url", url).putString("model", model).putString("key", apiKey).apply()
    }
}
