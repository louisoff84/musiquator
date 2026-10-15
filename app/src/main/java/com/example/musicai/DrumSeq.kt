package com.example.musicai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val PRESETS = listOf(
    "Rock" to mapOf(
        "kick" to "x.......x.x.....", "snare" to "....x.......x...", "hat" to "x.x.x.x.x.x.x.x."
    ),
    "Hip-hop" to mapOf(
        "kick" to "x..x..x...x.....", "snare" to "....x.......x...", "hat" to "x.x.x.x.x.x.x.xx"
    ),
    "House" to mapOf(
        "kick" to "x...x...x...x...", "clap" to "....x.......x...",
        "hat" to "x.x.x.x.x.x.x.x.", "openhat" to "..x...x...x...x."
    ),
    "Trap" to mapOf(
        "kick" to "x.....x..x......", "snare" to "........x.......",
        "hat" to "xxxxxxxxxxxxxxxx", "openhat" to "..............x."
    ),
    "Reggaeton" to mapOf(
        "kick" to "x...x...x...x...", "snare" to "...x..x....x..x.", "hat" to "x.x.x.x.x.x.x.x."
    ),
    "Disco" to mapOf(
        "kick" to "x...x...x...x...", "snare" to "....x.......x...",
        "hat" to "x.x.x.x.x.x.x.x.", "openhat" to "..x...x...x...x."
    )
)

@Composable
private fun RangeStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextLo, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        SmallBtn("−", onMinus, Modifier.width(34.dp))
        Text(
            "$value", color = TextHi, fontSize = 13.sp,
            modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        SmallBtn("+", onPlus, Modifier.width(34.dp))
    }
}

@Composable
fun DrumSequencer(studio: Studio, p: Project, track: PTrack) {
    val color = instInfo("drums").color
    val density = LocalDensity.current
    val len = studio.patternLen(track)
    val patBars = len / 16
    val cellDp = 36f
    val cellH = 42f
    val cellPx = with(density) { cellDp.dp.toPx() }
    val cellHPx = with(density) { cellH.dp.toPx() }
    val trackState = rememberUpdatedState(track)
    val lenState = rememberUpdatedState(len)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { Preview.warm("drums", 0, 0) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Motif", color = TextLo, fontSize = 11.sp)
            listOf(1, 2, 4).forEach { n ->
                Pill("$n mes.", patBars == n, { studio.setPatternBars(track.id, n) }, color = color)
            }
            Spacer(Modifier.width(6.dp))
            Text("Styles", color = TextLo, fontSize = 11.sp)
            PRESETS.forEach { (name, pat) ->
                Pill(name, false, { studio.applyPreset(track.id, pat) })
            }
            Pill("Vider", false, {
                studio.applyPreset(track.id, mapOf("kick" to ".".repeat(16)))
            })
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shownTo = min(track.toBar, p.bars)
            RangeStepper("Joue de la mesure", track.fromBar + 1,
                { studio.updateTrack(track.id, "from") { it.copy(fromBar = max(0, it.fromBar - 1)) } },
                { studio.updateTrack(track.id, "from") { it.copy(fromBar = min(min(it.toBar, p.bars) - 1, it.fromBar + 1)) } })
            RangeStepper("à la mesure", shownTo,
                { studio.updateTrack(track.id, "to") { it.copy(toBar = max(it.fromBar + 1, min(it.toBar, p.bars) - 1)) } },
                { studio.updateTrack(track.id, "to") { it.copy(toBar = min(p.bars, min(it.toBar, p.bars) + 1)) } })
        }
        Text(
            "Touche une case : 1 fois = coup, 2 fois = coup fort, 3 fois = vide",
            color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.width(96.dp)) {
                DRUM_NAMES.forEach { name ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(cellH.dp)
                            .clickable { Preview.playDrum(name) }
                            .padding(start = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(DRUM_LABELS[name] ?: name, color = TextHi, fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier
                        .width((len * cellDp).dp)
                        .height((DRUM_NAMES.size * cellH).dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { off ->
                                val t = trackState.value
                                val l = lenState.value
                                val col = floor(off.x / cellPx).toInt()
                                val row = floor(off.y / cellHPx).toInt()
                                if (col !in 0 until l || row !in DRUM_NAMES.indices) return@detectTapGestures
                                val drum = DRUM_NAMES[row]
                                val cur = (t.steps[drum] ?: "").padEnd(l, '.')[col]
                                val next = when (cur) { '.' -> 'x'; 'x' -> 'X'; else -> '.' }
                                studio.setStep(t.id, drum, col, next)
                                if (next != '.') Preview.playDrum(drum)
                            })
                        }
                ) {
                    val gap = 3f
                    val step = if (studio.playing) {
                        val beat = studio.playBeat - track.fromBar * 4f
                        if (beat >= 0f) (floor(beat * 4f).toInt() % len + len) % len else -1
                    } else -1
                    for (row in DRUM_NAMES.indices) {
                        val pat = (track.steps[DRUM_NAMES[row]] ?: "").padEnd(len, '.')
                        for (col in 0 until len) {
                            val group = (col / 4) % 2 == 0
                            val c = pat[col]
                            val base = if (group) Color(0xFF232736) else Color(0xFF1A1D28)
                            val fill = when (c) {
                                'X' -> color
                                'x' -> color.copy(alpha = 0.62f)
                                else -> base
                            }
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(col * cellPx + gap, row * cellHPx + gap),
                                size = Size(cellPx - gap * 2, cellHPx - gap * 2),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            if (col == step) {
                                drawRoundRect(
                                    color = Color(0x44FFFFFF),
                                    topLeft = Offset(col * cellPx + gap, row * cellHPx + gap),
                                    size = Size(cellPx - gap * 2, cellHPx - gap * 2),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
