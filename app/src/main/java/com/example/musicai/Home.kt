package com.example.musicai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HomeScreen(studio: Studio) {
    var showNew by remember { mutableStateOf(false) }
    var newWithAi by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ProjectMeta?>(null) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1F52), Bg)))
                .padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 18.dp)
        ) {
            Column {
                Text("Musiquator", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextHi)
                Text("Ton studio de musique de poche", fontSize = 14.sp, color = AccentSoft)
            }
            IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Réglages", tint = TextLo)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BigAction(
                "Nouveau projet", "Joue et compose toi-même", Icons.Rounded.Add,
                Brush.linearGradient(listOf(Accent, Color(0xFF6D3FE0))), Modifier.weight(1f)
            ) { newWithAi = false; showNew = true }
            BigAction(
                "Créer avec l'IA", "Décris, elle compose", Icons.Rounded.AutoAwesome,
                Brush.linearGradient(listOf(Color(0xFF0E9F8E), Color(0xFF0B7285))), Modifier.weight(1f)
            ) { newWithAi = true; showNew = true }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Mes projets", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextLo,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (studio.projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "Aucun projet pour l'instant.\nCrée-en un pour poser tes premières notes, tes beats ou ta voix.",
                    color = TextLo, fontSize = 14.sp, lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(studio.projects, key = { it.id }) { m ->
                    ProjectCard(m, onOpen = { studio.open(m.id) },
                        onDuplicate = { studio.duplicate(m.id) },
                        onDelete = { deleteTarget = m })
                }
            }
        }
    }

    if (showNew) NewProjectDialog(
        withAi = newWithAi,
        onDismiss = { showNew = false },
        onCreate = { name, bpm ->
            showNew = false
            studio.create(name, bpm)
            if (newWithAi) studio.tab = 4
        }
    )
    if (showSettings) SettingsDialog(studio) { showSettings = false }
    deleteTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Supprimer « ${m.title} » ?") },
            text = { Text("Le projet et sa voix seront effacés de cet appareil.") },
            confirmButton = {
                TextButton(onClick = { studio.delete(m.id); deleteTarget = null }) { Text("Supprimer", color = Hot) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun BigAction(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    brush: Brush,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = Color(0xCCFFFFFF), fontSize = 12.sp)
    }
}

@Composable
private fun ProjectCard(m: ProjectMeta, onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val date = remember(m.updated) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.updated))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF2A1F52)),
            contentAlignment = Alignment.Center
        ) { Text(if (m.hasVoice) "🎤" else "🎵", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            Text(
                "${m.bpm} BPM · ${m.bars} mesures · ${m.tracks} pistes",
                color = TextLo, fontSize = 12.sp
            )
            Text(date, color = TextLo.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        SmallBtn("⧉", onDuplicate, color = Color.Transparent, textColor = TextLo)
        SmallBtn("✕", onDelete, color = Color.Transparent, textColor = TextLo)
    }
}

@Composable
private fun NewProjectDialog(withAi: Boolean, onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var bpm by remember { mutableFloatStateOf(100f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withAi) "Nouveau morceau avec l'IA" else "Nouveau projet") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nom du projet") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    "Tempo", bpm, { bpm = it }, range = 60f..180f,
                    text = "${bpm.roundToInt()}"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, bpm.roundToInt()) }) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
