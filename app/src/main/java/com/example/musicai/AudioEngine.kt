package com.example.musicai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Synthétiseur + mixeur : génère l'instrumentale selon le MixPlan et la mélange avec la voix. */
object AudioEngine {
    const val SR = 44100

    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val ROOTS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Patterns sur 16 doubles-croches : kick, snare, hat
    private val PATTERNS = mapOf(
        "four_on_floor" to Triple("1000100010001000", "0000100000001000", "0010001000100010"),
        "hiphop" to Triple("1000000110100000", "0000100000001000", "1010101010101010"),
        "breakbeat" to Triple("1000001000100000", "0000100000001010", "1011101110111011"),
        "half_time" to Triple("1000000000001000", "0000000010000000", "1010101010101010")
    )

    fun render(plan: MixPlan, voiceIn: FloatArray?): FloatArray {
        val spb = (60.0 / plan.bpm * SR).toInt()          // échantillons par temps
        val barSamples = 4 * spb
        val voiceStart = (plan.voiceStartBar * barSamples).toInt()

        var bars = plan.bars
        if (voiceIn != null && !plan.voiceLoop) {
            bars = max(bars, ceil((voiceStart + voiceIn.size).toDouble() / barSamples).toInt() + 1)
        }
        bars = bars.coerceAtMost(128)
        val total = bars * barSamples

        val rootSemi = ROOTS.indexOf(plan.root).coerceAtLeast(0)
        val scale = if (plan.minor) MINOR else MAJOR
        fun degSemi(deg: Int): Int { val i = deg - 1; return scale[i % 7] + 12 * (i / 7) }
        fun freq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val base = 48 + rootSemi   // do3 transposé dans la tonalité
        val rnd = Random(plan.title.hashCode())

        val bass = FloatArray(total)
        val chords = FloatArray(total)
        val drums = FloatArray(total)
        val mel = FloatArray(total)

        for (bar in 0 until bars) {
            val deg = plan.chords[bar % plan.chords.size]
            val barStart = bar * barSamples

            // Accords (pad) : triade, saw filtrée
            for (k in intArrayOf(0, 2, 4)) {
                val midi = base + 12 + degSemi(deg + k)
                addTone(chords, barStart, barSamples, freq(midi), 0.18f, 1, 0.04, 0.15)
            }
            // Basse : croches syncopées
            val bassMidi = base - 12 + degSemi(deg)
            for (step in intArrayOf(0, 3, 4, 6)) {
                addTone(bass, barStart + step * spb / 2, spb / 2, freq(bassMidi), 0.5f, 0, 0.005, 0.05)
            }
            // Mélodie : croches, notes de l'accord + gamme
            if (plan.melody) {
                var step = 0
                while (step < 8) {
                    if (rnd.nextFloat() < 0.7f) {
                        val choices = intArrayOf(deg, deg + 2, deg + 4, deg + 1, deg + 3, deg + 7)
                        val d = choices[rnd.nextInt(choices.size)]
                        val len = if (rnd.nextFloat() < 0.3f) 2 else 1
                        addTone(mel, barStart + step * spb / 2, len * spb / 2, freq(base + 24 + degSemi(d)), 0.2f, 2, 0.005, 0.08)
                        step += len
                    } else step++
                }
            }
        }

        // Batterie
        PATTERNS[plan.drums]?.let { (k, s, h) ->
            val stepLen = spb / 4
            for (bar in 0 until bars) for (st in 0 until 16) {
                val pos = bar * barSamples + st * stepLen
                if (k[st] == '1') addKick(drums, pos)
                if (s[st] == '1') addSnare(drums, pos, rnd)
                if (h[st] == '1') addHat(drums, pos, rnd)
            }
        }

        // Voix : normalisation, placement, effets
        var voice: FloatArray? = null
        if (voiceIn != null) {
            var peak = 0f
            for (x in voiceIn) peak = max(peak, abs(x))
            val g = if (peak > 0f) 0.9f / peak else 1f
            val vt = FloatArray(total)
            if (plan.voiceLoop) {
                var i = voiceStart
                var j = 0
                while (i < total) { vt[i++] = voiceIn[j] * g; j = (j + 1) % voiceIn.size }
            } else {
                for (j in voiceIn.indices) {
                    val i = voiceStart + j
                    if (i >= total) break
                    vt[i] = voiceIn[j] * g
                }
            }
            voice = reverb(delay(vt, (spb * 0.75).toInt(), plan.voiceDelay), plan.voiceReverb)
        }

        // Mixage final avec "ducking" : la musique baisse quand la voix chante
        val out = FloatArray(total)
        var env = 0f
        for (i in 0 until total) {
            val v = voice?.get(i) ?: 0f
            val a = abs(v)
            env += (a - env) * (if (a > env) 0.01f else 0.0005f)
            val duck = 1f - 0.4f * min(1f, env * 3f)
            val music = bass[i] * plan.bassVol + chords[i] * plan.chordsVol +
                drums[i] * plan.drumsVol + mel[i] * plan.melodyVol
            out[i] = music * duck + v * plan.voiceVol
        }

        // Normalisation + fondu de sortie (1 s)
        var peak = 0f
        for (x in out) peak = max(peak, abs(x))
        val norm = if (peak > 0f) 0.9f / peak else 1f
        val fade = min(SR, total)
        for (i in 0 until total) {
            var x = out[i] * norm
            val left = total - i
            if (left < fade) x *= left.toFloat() / fade
            out[i] = x
        }
        return out
    }

    private fun addTone(buf: FloatArray, start: Int, len: Int, f: Double, vol: Float, kind: Int, att: Double, rel: Double) {
        if (start >= buf.size) return
        val n = min(len, buf.size - start)
        if (n <= 0) return
        val inc = f / SR
        var ph = 0.0
        var lp = 0f
        val a = (att * SR).toInt().coerceAtLeast(1)
        val r = (rel * SR).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            ph += inc
            if (ph >= 1.0) ph -= 1.0
            val w = when (kind) {
                0 -> sin(2 * Math.PI * ph)
                1 -> 2 * ph - 1
                else -> if (ph < 0.5) 1.0 else -1.0
            }
            var env = 1.0
            if (i < a) env = i.toDouble() / a
            val fromEnd = n - i
            if (fromEnd < r) env = min(env, fromEnd.toDouble() / r)
            val s = (w * env * vol).toFloat()
            lp += 0.2f * (s - lp)
            buf[start + i] += if (kind == 0) s else lp
        }
    }

    private fun addKick(buf: FloatArray, start: Int) {
        val n = min((0.25 * SR).toInt(), buf.size - start)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val fr = 45 + 105 * exp(-t * 28)
            ph += fr / SR
            buf[start + i] += (sin(2 * Math.PI * ph) * exp(-t * 11) * 0.9).toFloat()
        }
    }

    private fun addSnare(buf: FloatArray, start: Int, rnd: Random) {
        val n = min((0.18 * SR).toInt(), buf.size - start)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val noise = rnd.nextFloat() * 2f - 1f
            val tone = sin(2 * Math.PI * 190 * t)
            buf[start + i] += ((noise * 0.6 + tone * 0.3) * exp(-t * 22) * 0.7).toFloat()
        }
    }

    private fun addHat(buf: FloatArray, start: Int, rnd: Random) {
        val n = min((0.05 * SR).toInt(), buf.size - start)
        var prev = 0f
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val noise = rnd.nextFloat() * 2f - 1f
            val hp = noise - prev
            prev = noise
            buf[start + i] += (hp * exp(-t * 70) * 0.25).toFloat()
        }
    }

    private fun delay(x: FloatArray, d: Int, amount: Float): FloatArray {
        if (amount <= 0.01f || d <= 0) return x
        val echo = FloatArray(x.size)
        for (i in d until x.size) echo[i] = x[i - d] + echo[i - d] * 0.4f
        return FloatArray(x.size) { x[it] + echo[it] * amount * 0.6f }
    }

    private fun reverb(x: FloatArray, amount: Float): FloatArray {
        if (amount <= 0.01f) return x
        val wet = FloatArray(x.size)
        for (d in intArrayOf(1307, 1637, 1811, 1931)) {
            val buf = FloatArray(d)
            var idx = 0
            for (i in x.indices) {
                val y = buf[idx]
                buf[idx] = x[i] + y * 0.8f
                idx = (idx + 1) % d
                wet[i] += y
            }
        }
        return FloatArray(x.size) { x[it] + wet[it] * 0.12f * amount }
    }
}

object WavWriter {
    /** Écrit un WAV mono 16 bits. */
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        val dataLen = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataLen)
        for (x in samples) bb.putShort((x.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.outputStream().use { it.write(bb.array()) }
    }
}
