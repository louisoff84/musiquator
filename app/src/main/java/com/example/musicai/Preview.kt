package com.example.musicai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Pré-écoute instantanée : touches du clavier, pads, notes du piano roll, métronome. */
object Preview {
    private const val SR = AudioEngine.SR
    private val cache = HashMap<String, ShortArray>()
    private val handler = Handler(Looper.getMainLooper())
    private val live = AtomicInteger(0)

    private val DRUM_GAIN = mapOf(
        "kick" to 1.0f, "snare" to 0.9f, "hat" to 0.5f, "openhat" to 0.55f,
        "clap" to 0.85f, "tom" to 0.9f, "rim" to 0.65f
    )

    private fun renderNote(inst: String, pitch: Int): ShortArray {
        val t = Track(
            "p", inst, 1f, 0f, 0f, 0, 1, 0f,
            listOf(Note(intArrayOf(pitch), 0f, 1.2f, 0.9f)), emptyMap(), emptyList()
        )
        val song = Song("p", 120, 1, listOf(t), VoiceSettings(), "", "")
        val pcm = AudioEngine.render(song, null)
        return toShort(pcm, min(pcm.size, (SR * 1.9).toInt()), 1f)
    }

    private fun renderDrum(name: String): ShortArray {
        val t = Track(
            "p", "drums", 1f, 0f, 0f, 0, 1, 0f,
            emptyList(), emptyMap(), listOf(DrumHit(name, 0f, 1f))
        )
        val song = Song("p", 120, 1, listOf(t), VoiceSettings(), "", "")
        val pcm = AudioEngine.render(song, null)
        return toShort(pcm, min(pcm.size, (SR * 0.7).toInt()), DRUM_GAIN[name] ?: 0.8f)
    }

    private fun toShort(pcm: FloatArray, n: Int, gain: Float): ShortArray {
        val fadeLen = min(2000, n)
        return ShortArray(n) { i ->
            var x = pcm[i] * gain
            val left = n - i
            if (left < fadeLen) x *= left.toFloat() / fadeLen
            (x * 30000f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun noteSample(inst: String, pitch: Int): ShortArray = synchronized(cache) {
        cache.getOrPut("$inst/$pitch") { renderNote(inst, pitch) }
    }

    private fun drumSample(name: String): ShortArray = synchronized(cache) {
        cache.getOrPut("drums/$name") { renderDrum(name) }
    }

    /** Prépare les sons d'une plage de notes pour que le clavier réponde sans délai. */
    fun warm(inst: String, lo: Int, hi: Int) {
        if (inst == "drums") DRUM_NAMES.forEach { drumSample(it) }
        else for (p in lo..hi) noteSample(inst, p)
    }

    fun playNote(inst: String, pitch: Int) {
        if (inst == "drums") return
        play(noteSample(inst, pitch))
    }

    fun playDrum(name: String) = play(drumSample(name))

    fun click(accent: Boolean) {
        val key = if (accent) "click/a" else "click/b"
        val s = synchronized(cache) {
            cache.getOrPut(key) {
                val f = if (accent) 1600.0 else 1000.0
                val n = (SR * 0.035).toInt()
                ShortArray(n) { i ->
                    val t = i.toDouble() / SR
                    (sin(2 * PI * f * t) * exp(-t * 90) * 24000).toInt().toShort()
                }
            }
        }
        play(s)
    }

    private fun play(s: ShortArray) {
        if (s.isEmpty() || live.get() >= 24) return
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(s.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(s, 0, s.size)
            t.play()
            live.incrementAndGet()
            handler.postDelayed({
                runCatching { t.release() }
                live.decrementAndGet()
            }, s.size * 1000L / SR + 250)
        } catch (_: Exception) {
        }
    }
}
