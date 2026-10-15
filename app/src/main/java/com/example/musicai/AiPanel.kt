package com.example.musicai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val STYLE_IDEAS = listOf(
    "Rap mélancolique 90 BPM", "House dansante", "Lo-fi chill pour étudier", "Ballade piano émouvante",
    "Trap sombre 140 BPM", "Pop lumineuse", "Reggaeton d'été", "Rock énergique"
)
private val EDIT_IDEAS = listOf(
    "Ajoute une mélodie", "Batterie plus énergique", "Ajoute une basse", "Ajoute des cordes",
    "Plus d'ambiance et de réverb", "Rends-le plus triste", "Ajoute un pont", "Simplifie l'arrangement"
)

@Composable
fun AiTab(studio: Studio, p: Project) {
    var prompt by remember { mutableStateOf("") }
    var modify by remember { mutableStateOf(p.tracks.isNotEmpty()) }
    var showSettings by remember { mutableStateOf(false) }
    val canModify = p.tracks.isNotEmpty()
    val modifying = modify && canModify

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Panel {
            Text("✨ Assistant IA", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "L'IA écrit les pistes (notes, accords, batterie) et tu peux ensuite tout retoucher à la main. " +
                    "Tu peux annuler son travail avec ↶.",
                color = TextLo, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Nouveau morceau", !modifying, { modify = false })
                Pill("Modifier le morceau", modifying, { if (canModify) modify = true }, color = Mint)
            }
            if (!canModify) {
                Spacer(Modifier.height(6.dp))
                Text("Le projet est vide : l'IA va composer un morceau complet.", color = TextLo, fontSize = 11.sp)
            }
        }

        Panel {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = {
                    Text(if (modifying) "Que doit changer l'IA ?" else "Décris ta musique (style, ambiance, tempo…)")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (if (modifying) EDIT_IDEAS else STYLE_IDEAS).forEach { idea ->
                    Pill(idea, false, { prompt = idea })
                }
            }
        }

        Panel {
            Text("Paroles (optionnel)", color = TextHi, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = p.lyrics,
                onValueChange = { studio.setLyrics(it) },
                label = { Text("Colle tes paroles pour que l'IA les analyse") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (p.hasVoice) "🎤 Ta voix sera analysée sur l'appareil (tempo, tonalité, passages chantés) et envoyée à l'IA."
                else "Ajoute ta voix dans l'onglet Mixeur pour que l'IA compose autour d'elle.",
                color = TextLo, fontSize = 11.sp, lineHeight = 15.sp
            )
        }

        Button(
            onClick = { studio.runAi(prompt, modifying) },
            enabled = !studio.aiBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        ) {
            if (studio.aiBusy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (studio.aiBusy) "L'IA travaille…" else if (modifying) "Modifier avec l'IA" else "Composer avec l'IA",
                fontWeight = FontWeight.Bold
            )
        }

        if (studio.aiStatus.isNotBlank()) {
            Text(
                studio.aiStatus,
                color = if (studio.aiStatus.startsWith("Erreur")) Hot else TextLo,
                fontSize = 13.sp, lineHeight = 18.sp
            )
        }

        if (p.aiAnalysis.isNotBlank() || p.aiNotes.isNotBlank()) {
            Panel {
                if (p.aiAnalysis.isNotBlank()) {
                    Text("Analyse de l'IA", color = TextHi, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(p.aiAnalysis, color = TextLo, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (p.aiNotes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Choix de l'IA", color = TextHi, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(p.aiNotes, color = TextLo, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Serveur : ${studio.settings.url}\nModèle : ${studio.settings.model}",
                color = TextLo, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f)
            )
            SmallBtn("Réglages", { showSettings = true })
        }
        Spacer(Modifier.height(8.dp))
    }
    if (showSettings) SettingsDialog(studio) { showSettings = false }
}
