package com.example.musicai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Décode n'importe quel fichier audio (mp3, m4a, wav, ogg...) en PCM mono flottant. */
object AudioDecoder {
    fun decode(ctx: Context, uri: Uri, targetRate: Int): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var trackIdx = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIdx = i; fmt = f; break
            }
        }
        require(trackIdx >= 0 && fmt != null) { "Aucune piste audio dans ce fichier" }
        ex.selectTrack(trackIdx)

        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()

        var inRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> {
                        val b = codec.getOutputBuffer(o)!!
                        val chunk = ByteArray(info.size)
                        b.position(info.offset)
                        b.get(chunk)
                        pcm.write(chunk)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        inRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            codec.stop(); codec.release(); ex.release()
        }

        val shorts = ByteBuffer.wrap(pcm.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = shorts.remaining() / ch
        require(frames > 0) { "Fichier audio vide" }
        val mono = FloatArray(frames) { f ->
            var s = 0f
            for (c in 0 until ch) s += shorts.get(f * ch + c)
            s / (ch * 32768f)
        }
        if (inRate == targetRate) return mono

        val outLen = (frames.toLong() * targetRate / inRate).toInt()
        return FloatArray(outLen) { i ->
            val pos = i.toDouble() * inRate / targetRate
            val i0 = pos.toInt()
            val fr = (pos - i0).toFloat()
            val a = mono[i0.coerceAtMost(frames - 1)]
            val b = mono[(i0 + 1).coerceAtMost(frames - 1)]
            a + (b - a) * fr
        }
    }
}
