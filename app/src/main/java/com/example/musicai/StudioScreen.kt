package com.example.musicai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun StudioScreen(studio: Studio) {
    val p = studio.project ?: return
    var showRename by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---------- barre du haut ----------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallBtn("←", { studio.close() }, color = Color.Transparent)
                Column(Modifier.weight(1f).clickable { showRename = true }.padding(horizontal = 6.dp)) {
                    Text(p.title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                    Text(
                        "${p.bpm} BPM · ${p.bars} mesures · ${p.tracks.size} pistes",
                        color = TextLo, fontSize = 11.sp
                    )
                }
                SmallBtn("↶", { studio.undo() }, color = Color.Transparent, enabled = studio.canUndo)
                SmallBtn("↷", { studio.redo() }, color = Color.Transparent, enabled = studio.canRedo)
                Box {
                    IconButton(onClick = { exportMenu = true }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Exporter", tint = TextHi)
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(text = { Text("Exporter en WAV") }, onClick = {
                            exportMenu = false; studio.export(false)
                        })
                        DropdownMenuItem(text = { Text("Partager…") }, onClick = {
                            exportMenu = false; studio.export(true)
                        })
                    }
                }
            }

            Transport(studio, p)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (studio.tab) {
                    0 -> TracksTab(studio, p)
                    1 -> EditorTab(studio, p)
                    2 -> PlayTab(studio, p)
                    3 -> MixerTab(studio, p)
                    else -> AiTab(studio, p)
                }
            }

            val items = listOf(
                "Pistes" to "☰",
                "Éditeur" to "✎",
                "Jouer" to "🎹",
                "Mixeur" to "🎚",
                "IA" to "✨"
            )
            NavigationBar(containerColor = Surface1, windowInsets = WindowInsets(0, 0, 0, 0)) {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = studio.tab == i,
                        onClick = { studio.tab = i },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = AccentSoft,
                            indicatorColor = Accent,
                            unselectedIconColor = TextLo,
                            unselectedTextColor = TextLo
                        )
                    )
                }
            }
        }

        studio.toast?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg, color = TextHi, fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface3)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        studio.busy?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text(msg, color = TextHi, fontSize = 14.sp)
                }
            }
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(p.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Nom du projet") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { studio.rename(name); showRename = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextLo, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallBtn("−", onMinus, Modifier.width(32.dp))
            Text(
                value, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            SmallBtn("+", onPlus, Modifier.width(32.dp))
        }
    }
}

@Composable
private fun Transport(studio: Studio, p: Project) {
    val totalSec = p.bars * 4f * 60f / p.bpm
    val curSec = studio.playBeat * 60f / p.bpm
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (studio.playing) Hot else Accent)
                .clickable(enabled = !studio.recording) { studio.togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            if (studio.preparing) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else if (studio.playing) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Lecture", tint = Color.White, modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (studio.playing) fmtTime(curSec) else "0:00",
                color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Text("/ ${fmtTime(totalSec)}", color = TextLo, fontSize = 11.sp)
        }
        Stepper("TEMPO", "${p.bpm}", { studio.setBpm(p.bpm - 1) }, { studio.setBpm(p.bpm + 1) })
        Spacer(Modifier.width(6.dp))
        Stepper("MESURES", "${p.bars}", { studio.setBars(p.bars - 1) }, { studio.setBars(p.bars + 1) })
    }
}

// =========================================================================
// Onglet Pistes
// =========================================================================

@Composable
private fun TracksTab(studio: Studio, p: Project) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (p.tracks.isEmpty()) {
            item {
                Panel {
                    Text("Projet vide", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ajoute une piste (piano, basse, batterie…), puis dessine tes notes dans l'éditeur, " +
                            "joue-les au clavier avec REC, ou laisse l'IA composer.",
                        color = TextLo, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
        }
        items(p.tracks, key = { it.id }) { t ->
            TrackRow(studio, p, t)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallBtn("＋ Ajouter une piste", { showAdd = true }, Modifier.weight(1f), color = Accent, textColor = Color.White)
                SmallBtn("✨ Demander à l'IA", { studio.tab = 4 }, Modifier.weight(1f))
            }
        }
    }
    if (showAdd) AddTrackDialog(onDismiss = { showAdd = false }) { inst ->
        showAdd = false
        studio.addTrack(inst)
        studio.tab = 1
    }
}

@Composable
fun AddTrackDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle piste") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                INSTRUMENT_LIST.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { inst ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Surface3)
                                    .clickable { onPick(inst.key) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(inst.emoji, fontSize = 24.sp)
                                Text(inst.label, color = TextHi, fontSize = 12.sp)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun TrackRow(studio: Studio, p: Project, t: PTrack) {
    val info = instInfo(t.instrument)
    val selected = studio.selTrack == t.id
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(if (selected) 2.dp else 0.dp, if (selected) info.color else Color.Transparent, shape)
            .clickable { studio.selTrack = t.id; studio.selNote = -1; studio.tab = 1 }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(info.color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { Text(info.emoji, fontSize = 18.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                Text(
                    if (t.isDrums) "${info.label} · ${t.steps.size} sons"
                    else "${info.label} · ${t.notes.size} notes",
                    color = TextLo, fontSize = 11.sp
                )
            }
            SmallBtn("M", { studio.updateTrack(t.id) { it.copy(mute = !it.mute) } }, Modifier.width(36.dp),
                color = if (t.mute) Hot else Surface3)
            Spacer(Modifier.width(4.dp))
            SmallBtn("S", { studio.updateTrack(t.id) { it.copy(solo = !it.solo) } }, Modifier.width(36.dp),
                color = if (t.solo) Mint else Surface3, textColor = if (t.solo) Color.Black else TextHi)
            Box {
                SmallBtn("⋮", { menu = true }, Modifier.width(36.dp), color = Color.Transparent)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renommer") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Dupliquer") }, onClick = { menu = false; studio.duplicateTrack(t.id) })
                    DropdownMenuItem(text = { Text("Vider") }, onClick = { menu = false; studio.clearTrack(t.id) })
                    DropdownMenuItem(text = { Text("Supprimer", color = Hot) }, onClick = {
                        menu = false; studio.deleteTrack(t.id)
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MiniRoll(studio, t, p.bars, info.color, if (t.mute) 0.3f else 1f)
    }
    if (renaming) {
        var name by remember { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nom de la piste") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    studio.updateTrack(t.id) { it.copy(name = name.trim().ifBlank { it.name }) }
                    renaming = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } }
        )
    }
}

/** Aperçu miniature d'une piste sur toute la durée du morceau, avec la tête de lecture. */
@Composable
private fun MiniRoll(studio: Studio, t: PTrack, bars: Int, color: Color, alpha: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
    ) {
        val total = bars * 4f
        val w = size.width
        val h = size.height
        for (b in 1 until bars) {
            val x = w * b / bars
            drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x, h), 1f)
        }
        val c = color.copy(alpha = alpha)
        if (t.isDrums) {
            val from = t.fromBar * 4f
            val to = if (t.toBar >= bars) total else t.toBar * 4f
            val rows = DRUM_NAMES.size
            for ((name, pat) in t.steps) {
                val row = DRUM_NAMES.indexOf(name)
                if (row < 0 || pat.length < 4) continue
                var off = from
                while (off < to) {
                    for (i in pat.indices) {
                        if (pat[i] != 'x' && pat[i] != 'X') continue
                        val s = off + i * 0.25f
                        if (s >= to) break
                        drawRect(c, Offset(w * s / total, h * row / rows), Size(max(1.5f, w * 0.25f / total), h / rows - 0.5f))
                    }
                    off += pat.length * 0.25f
                }
            }
            for (hit in t.hits) {
                val row = DRUM_NAMES.indexOf(hit.drum)
                if (row < 0 || hit.start >= total) continue
                drawRect(c, Offset(w * hit.start / total, h * row / rows), Size(2f, h / rows - 0.5f))
            }
        } else if (t.notes.isNotEmpty()) {
            val lo = t.notes.minOf { it.pitch }
            val hi = t.notes.maxOf { it.pitch }
            val span = max(8, hi - lo + 1)
            val rowH = h / span
            for (n in t.notes) {
                if (n.start >= total) continue
                val y = h - (n.pitch - lo + 1) * rowH
                drawRect(
                    c, Offset(w * n.start / total, y),
                    Size(max(1.5f, w * min(n.dur, total - n.start) / total), max(1.5f, rowH - 0.5f))
                )
            }
        }
        if (studio.playing) {
            val x = w * (studio.playBeat / total).coerceIn(0f, 1f)
            drawLine(Hot, Offset(x, 0f), Offset(x, h), 2f)
        }
    }
}

// =========================================================================
// Onglet Éditeur : choix de la piste, puis piano roll ou séquenceur
// =========================================================================

@Composable
private fun EditorTab(studio: Studio, p: Project) {
    var showAdd by remember { mutableStateOf(false) }
    val track = p.tracks.firstOrNull { it.id == studio.selTrack } ?: p.tracks.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            p.tracks.forEach { t ->
                val info = instInfo(t.instrument)
                Pill(
                    "${info.emoji} ${t.name}", selected = track?.id == t.id,
                    onClick = { studio.selTrack = t.id; studio.selNote = -1 }, color = info.color
                )
            }
            Pill("＋", selected = false, onClick = { showAdd = true })
        }
        if (track == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Text("Ajoute une piste avec ＋ pour commencer à écrire de la musique.", color = TextLo, fontSize = 14.sp)
            }
        } else if (track.isDrums) {
            DrumSequencer(studio, p, track)
        } else {
            PianoRoll(studio, p, track)
        }
    }
    if (showAdd) AddTrackDialog(onDismiss = { showAdd = false }) { inst ->
        showAdd = false
        studio.addTrack(inst)
    }
}
