package com.example.musicai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MixerTab(studio: Studio, p: Project) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { VoiceCard(studio, p) }
        items(p.tracks, key = { it.id }) { t -> ChannelStrip(studio, t) }
        if (p.tracks.isEmpty()) {
            item { Text("Aucune piste à mixer pour l'instant.", color = TextLo, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ChannelStrip(studio: Studio, t: PTrack) {
    val info = instInfo(t.instrument)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(info.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(t.name, color = TextHi, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            SmallBtn("M", { studio.updateTrack(t.id) { it.copy(mute = !it.mute) } }, Modifier.width(36.dp),
                color = if (t.mute) Hot else Surface3)
            Spacer(Modifier.width(4.dp))
            SmallBtn("S", { studio.updateTrack(t.id) { it.copy(solo = !it.solo) } }, Modifier.width(36.dp),
                color = if (t.solo) Mint else Surface3, textColor = if (t.solo) Color.Black else TextHi)
        }
        LabeledSlider("Volume", t.volume, { v -> studio.updateTrack(t.id, "vol${t.id}") { it.copy(volume = v) } },
            range = 0f..1.5f, color = info.color)
        LabeledSlider("Réverb", t.reverb, { v -> studio.updateTrack(t.id, "rev${t.id}") { it.copy(reverb = v) } },
            color = info.color)
        LabeledSlider("Écho", t.delay, { v -> studio.updateTrack(t.id, "dly${t.id}") { it.copy(delay = v) } },
            color = info.color)
    }
}

@Composable
private fun VoiceCard(studio: Studio, p: Project) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) studio.importVoice(uri)
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) studio.startVoiceRecording() else studio.say("Permission micro refusée")
    }
    fun toggleRecord() {
        if (studio.voiceRecording) {
            studio.stopVoiceRecording()
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            studio.startVoiceRecording()
        } else {
            mic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Panel {
        Text("🎤 Ma voix", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        if (!p.hasVoice) {
            Text(
                "Importe un chant ou enregistre-toi : l'app le place sur ton morceau et le mixe avec la musique. " +
                    "L'IA peut l'analyser (tempo, tonalité) pour composer autour.",
                color = TextLo, fontSize = 12.sp, lineHeight = 17.sp
            )
        } else {
            val secs = (studio.voice?.size ?: 0).toFloat() / AudioEngine.SR
            Text("${p.voiceName} · ${fmtTime(secs)}", color = TextLo, fontSize = 12.sp, maxLines = 1)
            studio.voiceWave?.let { wave ->
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).background(Surface2)
                ) {
                    val n = wave.size
                    val step = size.width / n
                    val mid = size.height / 2
                    for (i in 0 until n) {
                        val h = max(2f, wave[i] * (size.height - 8f))
                        val x = i * step + step / 2
                        drawLine(Mint, Offset(x, mid - h / 2), Offset(x, mid + h / 2), max(1.5f, step * 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallBtn(if (p.hasVoice) "Remplacer" else "Importer", { pick.launch("audio/*") }, Modifier.weight(1f),
                enabled = !studio.voiceRecording)
            SmallBtn(
                if (studio.voiceRecording) "■ Arrêter" else "● Enregistrer", { toggleRecord() }, Modifier.weight(1f),
                color = if (studio.voiceRecording) Hot else Surface3,
                textColor = if (studio.voiceRecording) Color.White else TextHi
            )
            if (p.hasVoice) {
                SmallBtn("Retirer", { studio.removeVoice() }, Modifier.weight(1f), enabled = !studio.voiceRecording)
            }
        }
        if (p.hasVoice) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallBtn("Analyser la voix", { studio.analyzeVoice() }, Modifier.weight(1f))
                SmallBtn(
                    if (p.voice.loop) "Répéter : oui" else "Répéter : non",
                    { studio.setVoice { it.copy(loop = !it.loop) } }, Modifier.weight(1f),
                    color = if (p.voice.loop) Accent else Surface3,
                    textColor = if (p.voice.loop) Color.White else TextHi
                )
            }
            studio.voiceAnalysis?.let { a ->
                Spacer(Modifier.height(8.dp))
                Text(a.summary(), color = AccentSoft, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(6.dp))
            LabeledSlider("Volume", p.voice.volume, { v -> studio.setVoice { it.copy(volume = v) } },
                range = 0f..1.5f, color = Mint)
            LabeledSlider("Réverb", p.voice.reverb, { v -> studio.setVoice { it.copy(reverb = v) } }, color = Mint)
            LabeledSlider("Écho", p.voice.delay, { v -> studio.setVoice { it.copy(delay = v) } }, color = Mint)
            LabeledSlider(
                "Départ", p.voice.startBar, { v -> studio.setVoice { it.copy(startBar = v.roundToInt().toFloat()) } },
                range = 0f..max(1f, (p.bars - 1).toFloat()), color = Mint,
                text = "mes. ${p.voice.startBar.roundToInt() + 1}"
            )
            LabeledSlider("Ducking", p.voice.duck, { v -> studio.setVoice { it.copy(duck = v) } },
                range = 0f..0.8f, color = Mint)
            Text(
                "Ducking : la musique baisse quand ta voix chante.",
                color = TextLo, fontSize = 11.sp
            )
        }
    }
}
