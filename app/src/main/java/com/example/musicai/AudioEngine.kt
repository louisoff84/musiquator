package com.example.musicai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/** Synthétiseur multi-pistes : joue le Song composé par l'IA et le mélange avec la voix. */
object AudioEngine {
    const val SR = 44100
    private const val MAX_SECONDS = 180

    /** Enveloppe ADSR + oscillateurs. kinds : 0 sinus, 1 scie, 2 carré, 3 triangle, 4 clavier additif, 5 cloche FM. */
    private class Inst(
        val att: Double, val dec: Double, val sus: Double, val rel: Double,
        val gain: Float, val lp: Float,
        val kinds: IntArray, val detune: DoubleArray, val vibrato: Double
    )

    private val INSTS = mapOf(
        "bass" to Inst(0.005, 0.12, 0.65, 0.08, 0.6f, 0.12f, intArrayOf(1, 0), doubleArrayOf(1.0, 1.0), 0.0),
        "sub" to Inst(0.01, 0.10, 0.90, 0.12, 0.7f, 1f, intArrayOf(0), doubleArrayOf(1.0), 0.0),
        "pad" to Inst(0.25, 0.30, 0.80, 0.60, 0.35f, 0.15f, intArrayOf(1, 1, 1), doubleArrayOf(0.996, 1.0, 1.004), 0.0),
        "strings" to Inst(0.15, 0.20, 0.85, 0.40, 0.35f, 0.20f, intArrayOf(1, 1, 1), doubleArrayOf(0.997, 1.0, 1.003), 0.004),
        "lead" to Inst(0.01, 0.12, 0.70, 0.12, 0.40f, 0.30f, intArrayOf(2, 1), doubleArrayOf(1.0, 1.003), 0.003),
        "pluck" to Inst(0.002, 0.25, 0.0, 0.05, 0.50f, 0.30f, intArrayOf(1, 2), doubleArrayOf(1.0, 1.002), 0.0),
        "keys" to Inst(0.005, 0.50, 0.30, 0.25, 0.50f, 1f, intArrayOf(4), doubleArrayOf(1.0), 0.0),
        "bell" to Inst(0.001, 0.90, 0.0, 0.50, 0.40f, 1f, intArrayOf(5), doubleArrayOf(1.0), 0.0)
    )

    fun render(song: Song, voiceIn: FloatArray?): FloatArray {
        val spb = 60.0 / song.bpm * SR                 // échantillons par temps
        val barSamples = (4 * spb).toInt()
        val voiceStart = (song.voice.startBar * barSamples).toInt()

        var bars = song.bars
        if (voiceIn != null && !song.voice.loop) {
            bars = max(bars, ceil((voiceStart + voiceIn.size).toDouble() / barSamples).toInt() + 1)
        }
        bars = min(bars, max(4, (MAX_SECONDS * SR) / barSamples))
        val total = bars * barSamples
        val totalBeats = bars * 4.0
        val rnd = Random(song.title.hashCode())

        val music = FloatArray(total)
        for (tr in song.tracks) {
            val buf = FloatArray(total)
            val fromBeat = tr.fromBar * 4.0
            val toBeat = if (tr.toBar >= song.bars) totalBeats else tr.toBar * 4.0
            if (toBeat <= fromBeat) continue

            if (tr.instrument == "drums") {
                for ((drumName, pat) in tr.pattern) {
                    val patBeats = pat.length * 0.25
                    var off = fromBeat
                    while (off < toBeat) {
                        for (i in pat.indices) {
                            val c = pat[i]
                            if (c != 'x' && c != 'X') continue
                            val s = off + i * 0.25
                            if (s >= toBeat) break
                            addDrum(buf, (s * spb).toInt(), drumName, if (c == 'X') 1f else 0.75f, rnd)
                        }
                        off += patBeats
                    }
                }
                forEachOffset(tr.loopBeats, fromBeat, toBeat) { off ->
                    for (h in tr.hits) {
                        val s = off + h.start
                        if (s < fromBeat || s >= toBeat) continue
                        addDrum(buf, (s * spb).toInt(), h.drum, h.vel, rnd)
                    }
                }
            } else {
                val inst = INSTS.getValue(tr.instrument)
                forEachOffset(tr.loopBeats, fromBeat, toBeat) { off ->
                    for (n in tr.notes) {
                        val s = off + n.start
                        if (s < fromBeat || s >= toBeat) continue
                        val dur = min(n.dur.toDouble(), toBeat - s)
                        val durS = max(1, (dur * spb).toInt())
                        val v = n.vel / sqrt(n.pitches.size.toFloat())
                        for (p in n.pitches) {
                            val freq = 440.0 * 2.0.pow((p - 69) / 12.0)
                            renderNote(buf, (s * spb).toInt(), durS, freq, v, inst)
                        }
                    }
                }
            }

            delay(buf, (0.75 * spb).toInt(), tr.delay)
            reverb(buf, tr.reverb)
            for (i in 0 until total) music[i] += buf[i] * tr.volume
        }

        // Voix : normalisation, placement, effets
        var voice: FloatArray? = null
        if (voiceIn != null) {
            var peak = 0f
            for (x in voiceIn) peak = max(peak, abs(x))
            val g = if (peak > 0f) 0.9f / peak else 1f
            val vt = FloatArray(total)
            if (song.voice.loop) {
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
            delay(vt, (0.75 * spb).toInt(), song.voice.delay)
            reverb(vt, song.voice.reverb)
            voice = vt
        }

        // Mix final : la musique baisse quand la voix chante (ducking), puis limiteur doux
        var env = 0f
        var peak = 0f
        for (i in 0 until total) {
            val v = voice?.get(i) ?: 0f
            val a = abs(v)
            env += (a - env) * (if (a > env) 0.01f else 0.0005f)
            val duck = 1f - song.voice.duck * min(1f, env * 3f)
            val x = tanh(1.3f * (music[i] * duck + v * song.voice.volume))
            music[i] = x
            peak = max(peak, abs(x))
        }
        val norm = if (peak > 0f) 0.9f / peak else 1f
        val fade = min(SR, total)
        for (i in 0 until total) {
            var x = music[i] * norm
            val left = total - i
            if (left < fade) x *= left.toFloat() / fade
            music[i] = x
        }
        return music
    }

    private fun forEachOffset(loopBeats: Float, fromBeat: Double, toBeat: Double, body: (Double) -> Unit) {
        if (loopBeats >= 1f) {
            var off = fromBeat
            while (off < toBeat) { body(off); off += loopBeats }
        } else {
            body(0.0)
        }
    }

    private fun renderNote(buf: FloatArray, start: Int, durS: Int, freq: Double, vel: Float, inst: Inst) {
        if (start < 0 || start >= buf.size) return
        val relS = max(1, (inst.rel * SR).toInt())
        val n = min(durS + relS, buf.size - start)
        if (n <= 0) return
        val attS = max(1.0, inst.att * SR)
        val decS = max(1.0, inst.dec * SR)
        val nv = inst.kinds.size
        val phu = DoubleArray(nv)
        var lpv = 0f
        for (i in 0 until n) {
            var e = when {
                i < attS -> i / attS
                i < attS + decS -> 1.0 - (1.0 - inst.sus) * (i - attS) / decS
                else -> inst.sus
            }
            if (i >= durS) e *= max(0.0, 1.0 - (i - durS).toDouble() / relS)
            if (e <= 0.0 && i > attS) break

            val t = i.toDouble() / SR
            val vib = if (inst.vibrato > 0) 1.0 + inst.vibrato * sin(2 * PI * 5.5 * t) * min(1.0, t / 0.3) else 1.0
            var s = 0.0
            for (v in 0 until nv) {
                phu[v] += freq * inst.detune[v] * vib / SR
                val ph = phu[v] - floor(phu[v])
                s += when (inst.kinds[v]) {
                    0 -> sin(2 * PI * ph)
                    1 -> 2 * ph - 1
                    2 -> if (ph < 0.5) 1.0 else -1.0
                    3 -> 4 * abs(ph - 0.5) - 1
                    4 -> (sin(2 * PI * ph) + 0.4 * sin(4 * PI * ph) + 0.2 * sin(6 * PI * ph)) / 1.6
                    else -> sin(2 * PI * phu[v] + 3.0 * exp(-t * 3.0) * sin(2 * PI * 3.5 * phu[v]))
                }
            }
            s /= nv
            lpv += inst.lp * (s.toFloat() - lpv)
            buf[start + i] += lpv * (e * inst.gain * vel).toFloat()
        }
    }

    private fun addDrum(buf: FloatArray, start: Int, name: String, vel: Float, rnd: Random) {
        if (start < 0 || start >= buf.size) return
        val len = when (name) {
            "kick" -> 0.30; "snare" -> 0.20; "hat" -> 0.05; "openhat" -> 0.30
            "clap" -> 0.20; "tom" -> 0.35; "rim" -> 0.05; else -> return
        }
        val n = min((len * SR).toInt(), buf.size - start)
        var ph = 0.0
        var prev = 0f
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val noise = rnd.nextFloat() * 2f - 1f
            val hp = noise - prev
            prev = noise
            val x: Double = when (name) {
                "kick" -> {
                    ph += (45 + 105 * exp(-t * 28)) / SR
                    sin(2 * PI * ph) * exp(-t * 11) * 0.9
                }
                "snare" -> (noise * 0.6 + sin(2 * PI * 190 * t) * 0.3) * exp(-t * 22) * 0.7
                "hat" -> hp * exp(-t * 70) * 0.25
                "openhat" -> hp * exp(-t * 12) * 0.22
                "clap" -> {
                    val env = if (t < 0.03) exp(-(t % 0.01) * 400) else exp(-(t - 0.03) * 20) * 0.8
                    noise * env * 0.5
                }
                "tom" -> {
                    ph += (110 + 70 * exp(-t * 14)) / SR
                    sin(2 * PI * ph) * exp(-t * 9) * 0.7
                }
                else -> sin(2 * PI * 820 * t) * exp(-t * 90) * 0.5 + noise * exp(-t * 120) * 0.2
            }
            buf[start + i] += (x * vel).toFloat()
        }
    }

    /** Écho en place (croche pointée). */
    private fun delay(x: FloatArray, d: Int, amount: Float) {
        if (amount <= 0.01f || d <= 0) return
        val echo = FloatArray(x.size)
        for (i in d until x.size) echo[i] = x[i - d] + echo[i - d] * 0.4f
        for (i in x.indices) x[i] += echo[i] * amount * 0.6f
    }

    /** Réverbération simple (4 filtres en peigne) en place. */
    private fun reverb(x: FloatArray, amount: Float) {
        if (amount <= 0.01f) return
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
        for (i in x.indices) x[i] += wet[i] * 0.12f * amount
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
