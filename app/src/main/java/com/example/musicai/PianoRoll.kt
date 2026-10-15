package com.example.musicai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class RollCfg(
    val beatPx: Float, val rowPx: Float, val grid: Float, val noteLen: Float,
    val lo: Int, val hi: Int, val totalBeats: Int, val track: PTrack
)

private fun findNote(c: RollCfg, x: Float, y: Float): PNote? {
    val beat = x / c.beatPx
    val pitch = c.hi - floor(y / c.rowPx).toInt()
    return c.track.notes.lastOrNull { it.pitch == pitch && beat >= it.start && beat <= it.start + it.dur }
}

private val GRIDS = listOf(0.25f to "1/16", 0.5f to "1/8", 1f to "1/4", 2f to "1/2", 4f to "1 mes.")

@Composable
fun PianoRoll(studio: Studio, p: Project, track: PTrack) {
    val info = instInfo(track.instrument)
    val density = LocalDensity.current
    var gridIdx by remember { mutableIntStateOf(1) }
    var lenIdx by remember { mutableIntStateOf(2) }
    var zoomIdx by remember { mutableIntStateOf(1) }
    val grid = GRIDS[gridIdx].first
    val noteLen = GRIDS[lenIdx].first
    val beatDp = listOf(30f, 52f, 84f)[zoomIdx]
    val rowDp = 22f
    val totalBeats = p.bars * 4

    val range = remember(track.id) {
        val base = when (track.instrument) {
            "bass", "sub" -> 24 to 60
            "pad", "strings", "keys" -> 41 to 88
            else -> 48 to 91
        }
        val nl = track.notes.minOfOrNull { it.pitch } ?: base.first
        val nh = track.notes.maxOfOrNull { it.pitch } ?: base.second
        Pair(min(base.first, nl - 2).coerceAtLeast(12), max(base.second, nh + 2).coerceAtMost(108))
    }
    val lo = range.first
    val hi = range.second
    val beatPx = with(density) { beatDp.dp.toPx() }
    val rowPx = with(density) { rowDp.dp.toPx() }
    val cfg = rememberUpdatedState(RollCfg(beatPx, rowPx, grid, noteLen, lo, hi, totalBeats, track))

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val sel = track.notes.firstOrNull { it.id == studio.selNote }

    LaunchedEffect(track.id, track.instrument) {
        withContext(Dispatchers.Default) { Preview.warm(track.instrument, lo, hi) }
    }
    LaunchedEffect(track.id) {
        snapshotFlow { vScroll.maxValue }.first { it > 0 }
        val center = if (track.notes.isEmpty()) (lo + hi) / 2
        else track.notes.map { it.pitch }.sorted()[track.notes.size / 2]
        val y = (hi - center) * rowPx - with(density) { 110.dp.toPx() }
        vScroll.scrollTo(max(0f, y).toInt())
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- outils ----------
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Grille", color = TextLo, fontSize = 11.sp)
            GRIDS.forEachIndexed { i, g -> Pill(g.second, gridIdx == i, { gridIdx = i }, color = info.color) }
            Spacer(Modifier.width(6.dp))
            Text("Note", color = TextLo, fontSize = 11.sp)
            GRIDS.forEachIndexed { i, g -> Pill(g.second, lenIdx == i, { lenIdx = i }, color = info.color) }
            Spacer(Modifier.width(6.dp))
            Pill("Zoom −", false, { zoomIdx = max(0, zoomIdx - 1) })
            Pill("Zoom +", false, { zoomIdx = min(2, zoomIdx + 1) })
            Pill("Répéter ×2", false, { studio.repeatPattern(track.id) })
            Pill("Vider", false, { studio.clearTrack(track.id) })
        }

        // ---------- note sélectionnée ----------
        if (sel != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text(
                    "${midiName(sel.pitch)} · mesure ${(sel.start / 4).toInt() + 1}, temps ${"%.2f".format(sel.start % 4 + 1)} · durée ${"%.2f".format(sel.dur)}",
                    color = TextHi, fontSize = 12.sp
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallBtn("◀", { studio.updateNote(track.id, sel.id) { it.copy(start = max(0f, it.start - grid)) } })
                    SmallBtn("▶", { studio.updateNote(track.id, sel.id) { it.copy(start = min(totalBeats - 0.25f, it.start + grid)) } })
                    SmallBtn("▲", {
                        val np = min(hi, sel.pitch + 1)
                        studio.updateNote(track.id, sel.id) { it.copy(pitch = np) }
                        Preview.playNote(track.instrument, np)
                    })
                    SmallBtn("▼", {
                        val np = max(lo, sel.pitch - 1)
                        studio.updateNote(track.id, sel.id) { it.copy(pitch = np) }
                        Preview.playNote(track.instrument, np)
                    })
                    SmallBtn("Court", { studio.updateNote(track.id, sel.id) { it.copy(dur = max(0.25f, it.dur - grid)) } })
                    SmallBtn("Long", { studio.updateNote(track.id, sel.id) { it.copy(dur = it.dur + grid) } })
                    SmallBtn("Copier", { studio.duplicateNote(track.id, sel.id) })
                    SmallBtn("Suppr.", { studio.deleteNote(track.id, sel.id) }, color = Hot, textColor = Color.White)
                    SmallBtn("OK", { studio.selNote = -1 })
                }
                LabeledSlider(
                    "Force", sel.vel,
                    { v -> studio.updateNote(track.id, sel.id, "vel") { it.copy(vel = v) } },
                    color = info.color
                )
            }
        } else {
            Text(
                "Touche la grille pour poser une note · appui long puis glisse pour déplacer",
                color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // ---------- règle des mesures ----------
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(46.dp))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Canvas(
                    Modifier
                        .width((totalBeats * beatDp).dp)
                        .height(20.dp)
                        .background(Surface2)
                ) {
                    for (bar in 0 until p.bars) {
                        val x = bar * 4 * beatPx
                        drawLine(Color(0x55FFFFFF), Offset(x, 0f), Offset(x, size.height), 2f)
                        drawText(
                            textMeasurer = textMeasurer, text = "${bar + 1}",
                            topLeft = Offset(x + 5f, 2f),
                            style = TextStyle(color = TextLo, fontSize = 10.sp)
                        )
                    }
                }
            }
        }

        // ---------- touches + grille ----------
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.width(46.dp)) {
                for (pitch in hi downTo lo) {
                    val black = isBlackKey(pitch)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rowDp.dp)
                            .background(if (black) Color(0xFF15171E) else Color(0xFFE6E7EE))
                            .clickable { Preview.playNote(track.instrument, pitch) },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (!black) {
                            Text(
                                midiName(pitch),
                                color = if (pitch % 12 == 0) Accent else Color(0xFF555B6E),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Canvas(
                    Modifier
                        .width((totalBeats * beatDp).dp)
                        .height(((hi - lo + 1) * rowDp).dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { off ->
                                val c = cfg.value
                                val hitNote = findNote(c, off.x, off.y)
                                if (hitNote != null) {
                                    studio.selNote = hitNote.id
                                    Preview.playNote(c.track.instrument, hitNote.pitch)
                                } else {
                                    val pitch = c.hi - floor(off.y / c.rowPx).toInt()
                                    if (pitch in c.lo..c.hi) {
                                        val start = floor(off.x / c.beatPx / c.grid) * c.grid
                                        if (start < c.totalBeats) {
                                            studio.selNote = studio.addNote(c.track.id, pitch, start, c.noteLen)
                                            Preview.playNote(c.track.instrument, pitch)
                                        }
                                    }
                                }
                            })
                        }
                        .pointerInput(Unit) {
                            var dragId = -1
                            var accX = 0f
                            var accY = 0f
                            var origStart = 0f
                            var origPitch = 0
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    val n = findNote(cfg.value, off.x, off.y)
                                    if (n != null) {
                                        dragId = n.id
                                        origStart = n.start
                                        origPitch = n.pitch
                                        accX = 0f
                                        accY = 0f
                                        studio.selNote = n.id
                                    } else dragId = -1
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (dragId >= 0) {
                                        val c = cfg.value
                                        accX += amount.x
                                        accY += amount.y
                                        val ns = ((origStart + accX / c.beatPx) / c.grid).roundToInt() * c.grid
                                        val np = origPitch - (accY / c.rowPx).roundToInt()
                                        val id = dragId
                                        studio.updateNote(c.track.id, id, "drag") {
                                            it.copy(
                                                start = ns.coerceIn(0f, c.totalBeats - 0.25f),
                                                pitch = np.coerceIn(c.lo, c.hi)
                                            )
                                        }
                                    }
                                },
                                onDragEnd = { dragId = -1 },
                                onDragCancel = { dragId = -1 }
                            )
                        }
                ) {
                    val darkRow = Color(0xFF14161D)
                    val lightRow = Color(0xFF1B1E28)
                    for (pitch in hi downTo lo) {
                        val y = (hi - pitch) * rowPx
                        drawRect(if (isBlackKey(pitch)) darkRow else lightRow, Offset(0f, y), Size(size.width, rowPx))
                        if (pitch % 12 == 0) {
                            drawLine(Color(0x44FFFFFF), Offset(0f, y + rowPx), Offset(size.width, y + rowPx), 1f)
                        }
                    }
                    if (grid < 1f) {
                        var b = 0f
                        while (b < totalBeats) {
                            val x = b * beatPx
                            drawLine(Color(0x12FFFFFF), Offset(x, 0f), Offset(x, size.height), 1f)
                            b += grid
                        }
                    }
                    for (beat in 0..totalBeats) {
                        val x = beat * beatPx
                        val isBar = beat % 4 == 0
                        drawLine(
                            if (isBar) Color(0x55FFFFFF) else Color(0x24FFFFFF),
                            Offset(x, 0f), Offset(x, size.height), if (isBar) 2f else 1f
                        )
                    }
                    for (n in track.notes) {
                        if (n.pitch !in lo..hi || n.start >= totalBeats) continue
                        val x = n.start * beatPx
                        val y = (hi - n.pitch) * rowPx
                        val w = max(5f, n.dur * beatPx - 2f)
                        drawRoundRect(
                            color = info.color.copy(alpha = 0.40f + 0.60f * n.vel),
                            topLeft = Offset(x + 1f, y + 1.5f),
                            size = Size(w, rowPx - 3f),
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                        if (n.id == studio.selNote) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(x + 1f, y + 1.5f),
                                size = Size(w, rowPx - 3f),
                                cornerRadius = CornerRadius(5f, 5f),
                                style = Stroke(3f)
                            )
                        }
                    }
                    val ph = if (studio.recording) studio.recBeat else if (studio.playing) studio.playBeat else -1f
                    if (ph >= 0f) {
                        val x = ph * beatPx
                        drawLine(Hot, Offset(x, 0f), Offset(x, size.height), 3f)
                    }
                }
            }
        }
    }
}
