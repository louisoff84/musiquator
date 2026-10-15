package com.example.musicai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Résultat de l'analyse audio de la voix, calculée sur l'appareil. */
class VoiceAnalysis(
    val durationSec: Float,
    val loudnessDb: Float,
    val tempoBpm: Int?,
    val tempoConfidence: Float,
    val keyRoot: String?,
    val keyMinor: Boolean,
    val keyConfidence: Float,
    val lowNote: String?,
    val highNote: String?,
    val segments: List<Pair<Float, Float>>,
    val syllablesPerSecond: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("durationSec", round1(durationSec))
        put("loudnessDb", round1(loudnessDb))
        if (tempoBpm != null) {
            put("tempoBpm", tempoBpm)
            put("tempoConfidence", round2(tempoConfidence))
        }
        if (keyRoot != null) {
            put("key", JSONObject()
                .put("root", keyRoot)
                .put("mode", if (keyMinor) "minor" else "major")
                .put("confidence", round2(keyConfidence)))
        }
        if (lowNote != null && highNote != null) {
            put("pitchRange", JSONObject().put("low", lowNote).put("high", highNote))
        }
        put("vocalSegmentsSec", JSONArray().also { arr ->
            segments.forEach { (a, b) -> arr.put(JSONArray().put(round1(a)).put(round1(b))) }
        })
        put("syllablesPerSecond", round1(syllablesPerSecond))
    }

    /** Résumé lisible pour l'écran. */
    fun summary(): String = buildList {
        add("Durée %.1f s".format(durationSec))
        if (tempoBpm != null) add("tempo ≈ $tempoBpm BPM" + if (tempoConfidence < 0.3f) " (incertain)" else "")
        if (keyRoot != null) add("tonalité $keyRoot ${if (keyMinor) "mineur" else "majeur"}")
        if (lowNote != null && highNote != null) add("voix $lowNote–$highNote")
        add("${segments.size} passage(s) chanté(s)")
        add("%.1f syllabes/s".format(syllablesPerSecond))
    }.joinToString(" · ")

    private fun round1(x: Float) = (x * 10).roundToInt() / 10.0
    private fun round2(x: Float) = (x * 100).roundToInt() / 100.0
}

object VoiceAnalyzer {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    private fun noteName(midi: Int) = NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)

    fun analyze(samples: FloatArray, sr: Int): VoiceAnalysis {
        val duration = samples.size.toFloat() / sr
        val hop = sr / 100                               // trames de 10 ms
        val nFrames = samples.size / hop
        if (nFrames < 10) {
            return VoiceAnalysis(duration, -90f, null, 0f, null, true, 0f, null, null, emptyList(), 0f)
        }

        // Enveloppe d'énergie
        val env = FloatArray(nFrames)
        for (f in 0 until nFrames) {
            var s = 0.0
            val o = f * hop
            for (i in 0 until hop) { val v = samples[o + i].toDouble(); s += v * v }
            env[f] = sqrt(s / hop).toFloat()
        }
        val sorted = env.sortedArray()
        val p90 = sorted[(nFrames * 0.9).toInt().coerceAtMost(nFrames - 1)]
        val thr = max(0.002f, 0.12f * p90)
        val active = BooleanArray(nFrames) { env[it] > thr }

        // Passages chantés : trous < 0.3 s comblés, passages < 0.3 s ignorés
        val segs = ArrayList<Pair<Float, Float>>()
        var f = 0
        while (f < nFrames) {
            if (!active[f]) { f++; continue }
            val start = f
            var end = f
            var gap = 0
            while (f < nFrames && gap <= 30) {
                if (active[f]) { end = f; gap = 0 } else gap++
                f++
            }
            if (end - start >= 30 && segs.size < 40) segs.add(Pair(start / 100f, (end + 1) / 100f))
        }

        // Volume moyen sur les parties actives
        var sumSq = 0.0
        var cnt = 0
        for (i in 0 until nFrames) if (active[i]) { sumSq += env[i].toDouble() * env[i]; cnt++ }
        val loudness = if (cnt > 0) (20 * log10(sqrt(sumSq / cnt))).toFloat() else -90f

        // Débit : pics d'énergie (≈ syllabes) par seconde de voix
        var peaks = 0
        for (i in 8 until nFrames - 8) {
            if (env[i] <= thr * 2 || env[i] <= env[i - 1] || env[i] < env[i + 1]) continue
            var isMax = true
            for (k in i - 8..i + 8) if (env[k] > env[i]) { isMax = false; break }
            if (isMax) peaks++
        }
        val syllables = if (cnt > 0) peaks / (cnt / 100f) else 0f

        val tempo = estimateTempo(env)
        val pitch = analyzePitch(samples, sr, thr)

        return VoiceAnalysis(
            durationSec = duration,
            loudnessDb = loudness,
            tempoBpm = tempo?.first,
            tempoConfidence = tempo?.second ?: 0f,
            keyRoot = pitch.keyRoot,
            keyMinor = pitch.keyMinor,
            keyConfidence = pitch.keyConfidence,
            lowNote = pitch.low,
            highNote = pitch.high,
            segments = segs,
            syllablesPerSecond = syllables
        )
    }

    /** Tempo par autocorrélation de l'attaque d'énergie (60 à 180 BPM). */
    private fun estimateTempo(env: FloatArray): Pair<Int, Float>? {
        val n = env.size
        if (n < 300) return null
        val flux = FloatArray(n)
        var prev = ln(1.0 + 50.0 * env[0]).toFloat()
        for (i in 1 until n) {
            val l = ln(1.0 + 50.0 * env[i]).toFloat()
            flux[i] = max(0f, l - prev)
            prev = l
        }
        val minLag = 33
        val maxLag = 100
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var s = 0.0
            for (i in 0 until n - lag) s += flux[i].toDouble() * flux[i + lag]
            ac[lag] = s / (n - lag)
        }
        var best = minLag
        for (lag in minLag..maxLag) if (ac[lag] > ac[best]) best = lag
        var mean = 0.0
        for (lag in minLag..maxLag) mean += ac[lag]
        mean /= (maxLag - minLag + 1)
        if (mean <= 0.0) return null
        val ratio = ac[best] / mean
        val a = ac[best - 1]; val b = ac[best]; val c = ac[best + 1]
        val denom = a - 2 * b + c
        val lagF = if (denom != 0.0) best + 0.5 * (a - c) / denom else best.toDouble()
        val bpm = (6000.0 / lagF).roundToInt()
        return Pair(bpm, min(1.0, (ratio - 1) / 2).toFloat().coerceAtLeast(0f))
    }

    private class PitchResult(
        val keyRoot: String?, val keyMinor: Boolean, val keyConfidence: Float,
        val low: String?, val high: String?
    )

    /** Hauteur de la voix (autocorrélation normalisée) puis tonalité (profils de Krumhansl). */
    private fun analyzePitch(samples: FloatArray, sr: Int, thr: Float): PitchResult {
        val ds = max(1, sr / 11025)
        val srD = sr / ds
        val m = min(samples.size, 150 * sr) / ds
        val y = FloatArray(m)
        for (i in 0 until m) {
            var s = 0f
            for (k in 0 until ds) s += samples[i * ds + k]
            y[i] = s / ds
        }
        val w = 512
        val h = 256
        val minLag = max(2, srD / 800)
        val maxLag = srD / 70
        val pre = DoubleArray(w + 1)
        val nsdf = DoubleArray(maxLag + 2)
        val hist = DoubleArray(12)
        val midis = ArrayList<Int>()

        var start = 0
        while (start + w <= m) {
            for (i in 0 until w) { val v = y[start + i].toDouble(); pre[i + 1] = pre[i] + v * v }
            val rms = sqrt(pre[w] / w)
            if (rms > thr) {
                for (tau in minLag - 1..maxLag + 1) {
                    var num = 0.0
                    for (i in 0 until w - tau) num += y[start + i].toDouble() * y[start + i + tau]
                    val den = pre[w - tau] + (pre[w] - pre[tau])
                    nsdf[tau] = if (den > 1e-12) 2 * num / den else 0.0
                }
                var maxV = 0.0
                for (tau in minLag..maxLag) {
                    if (nsdf[tau] > nsdf[tau - 1] && nsdf[tau] >= nsdf[tau + 1] && nsdf[tau] > maxV) maxV = nsdf[tau]
                }
                if (maxV >= 0.8) {
                    var pick = -1
                    for (tau in minLag..maxLag) {
                        if (nsdf[tau] > nsdf[tau - 1] && nsdf[tau] >= nsdf[tau + 1] && nsdf[tau] >= 0.9 * maxV) {
                            pick = tau; break
                        }
                    }
                    if (pick > 0) {
                        val freq = srD.toDouble() / pick
                        val midi = (69 + 12 * ln(freq / 440.0) / ln(2.0)).roundToInt()
                        hist[((midi % 12) + 12) % 12] += maxV
                        midis.add(midi)
                    }
                }
            }
            start += h
        }

        if (midis.size < 20) return PitchResult(null, true, 0f, null, null)

        midis.sort()
        val low = noteName(midis[(midis.size * 0.1).toInt()])
        val high = noteName(midis[(midis.size * 0.9).toInt().coerceAtMost(midis.size - 1)])

        var bestCorr = -2.0
        var bestRoot = 0
        var bestMinor = false
        val rot = DoubleArray(12)
        for (r in 0 until 12) {
            for (i in 0 until 12) rot[i] = hist[(r + i) % 12]
            val cMaj = pearson(rot, MAJOR)
            val cMin = pearson(rot, MINOR)
            if (cMaj > bestCorr) { bestCorr = cMaj; bestRoot = r; bestMinor = false }
            if (cMin > bestCorr) { bestCorr = cMin; bestRoot = r; bestMinor = true }
        }
        return if (bestCorr >= 0.4) {
            PitchResult(NAMES[bestRoot], bestMinor, bestCorr.toFloat().coerceIn(0f, 1f), low, high)
        } else {
            PitchResult(null, true, 0f, low, high)
        }
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average()
        val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        val d = sqrt(da * db)
        return if (d > 1e-12) num / d else 0.0
    }
}
