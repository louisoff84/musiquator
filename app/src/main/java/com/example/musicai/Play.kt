package com.example.musicai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WHITE_OFFSETS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private const val WHITE_COUNT = 10

@Composable
fun PlayTab(studio: Studio, p: Project) {
    var mode by remember { mutableIntStateOf(0) }
    var octave by remember { mutableIntStateOf(4) }
    val selected = p.tracks.firstOrNull { it.id == studio.selTrack && !it.isDrums }
    var inst by remember { mutableStateOf(selected?.instrument ?: "keys") }
    val baseMidi = 12 * (octave + 1)

    LaunchedEffect(selected?.id) { selected?.let { inst = it.instrument } }
    LaunchedEffect(inst, octave, mode) {
        withContext(Dispatchers.Default) {
            if (mode == 0) Preview.warm(inst, baseMidi, baseMidi + 20) else Preview.warm("drums", 0, 0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- enregistrement ----------
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (studio.recording) Color(0xFF7F1D2D) else Hot)
                        .clickable {
                            if (studio.recording) studio.stopRec()
                            else studio.startRec(drums = mode == 1, inst = inst)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (studio.recording) {
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    } else {
                        Text("REC", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val line = when {
                        studio.recording && studio.countIn > 0 -> "Prépare-toi… ${studio.countIn}"
                        studio.recording -> "Enregistrement · mesure ${(studio.recBeat / 4).toInt() + 1}"
                        else -> "Enregistre ton jeu"
                    }
                    Text(line, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (studio.recording) "Appuie sur le carré pour terminer."
                        else "4 temps de décompte, puis joue : tes notes sont calées sur la grille.",
                        color = TextLo, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(
                    if (studio.listenWhileRec) "🎧 J'écoute le morceau" else "🎧 Sans le morceau",
                    studio.listenWhileRec, { studio.listenWhileRec = !studio.listenWhileRec }, color = Mint
                )
                Text("Départ mesure", color = TextLo, fontSize = 11.sp)
                SmallBtn("−", { studio.recStartBar = (studio.recStartBar - 1).coerceAtLeast(0) }, Modifier.width(34.dp))
                Text("${studio.recStartBar + 1}", color = TextHi, fontSize = 13.sp)
                SmallBtn("+", { studio.recStartBar = (studio.recStartBar + 1).coerceAtMost(p.bars - 1) }, Modifier.width(34.dp))
            }
        }

        // ---------- choix de l'instrument ----------
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill("🎹 Clavier", mode == 0, { mode = 0 })
            Pill("🥁 Pads", mode == 1, { mode = 1 }, color = instInfo("drums").color)
            if (mode == 0) {
                Spacer(Modifier.width(6.dp))
                INSTRUMENT_LIST.filter { it.key != "drums" }.forEach { i ->
                    Pill(i.label, inst == i.key, { inst = i.key }, color = i.color)
                }
            }
        }

        if (mode == 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Octave", color = TextLo, fontSize = 12.sp)
                SmallBtn("−", { octave = (octave - 1).coerceAtLeast(1) }, Modifier.width(38.dp))
                Text("${octave}", color = TextHi, fontWeight = FontWeight.Bold)
                SmallBtn("+", { octave = (octave + 1).coerceAtMost(6) }, Modifier.width(38.dp))
                Text(
                    if (selected != null) "Piste : ${selected.name}" else "Une piste ${instInfo(inst).label} sera créée",
                    color = TextLo, fontSize = 11.sp
                )
            }
            Keyboard(studio, inst, baseMidi, instInfo(inst).color)
        } else {
            val drumTrack = p.tracks.firstOrNull { it.isDrums }
            Text(
                if (drumTrack != null) "Piste : ${drumTrack.name}" else "Une piste Batterie sera créée",
                color = TextLo, fontSize = 11.sp
            )
            Pads(studio)
        }
    }
}

private fun Modifier.keyPress(key: Any, inst: String, onDown: () -> Unit, onUp: () -> Unit): Modifier =
    pointerInput(key, inst) {
        detectTapGestures(onPress = {
            onDown()
            try {
                tryAwaitRelease()
            } finally {
                onUp()
            }
        })
    }

@Composable
private fun Keyboard(studio: Studio, inst: String, baseMidi: Int, color: Color) {
    val pressed = remember { mutableStateMapOf<Int, Boolean>() }
    BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp))) {
        val ww = maxWidth / WHITE_COUNT
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until WHITE_COUNT) {
                val midi = baseMidi + (i / 7) * 12 + WHITE_OFFSETS[i % 7]
                Box(
                    Modifier
                        .width(ww)
                        .fillMaxSize()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(if (pressed[midi] == true) color else Color(0xFFF1F2F7))
                        .keyPress(midi, inst, {
                            pressed[midi] = true
                            Preview.playNote(inst, midi)
                            studio.keyDown(midi)
                        }, {
                            pressed[midi] = false
                            studio.keyUp(midi)
                        }),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (WHITE_OFFSETS[i % 7] == 0) {
                        Text(midiName(midi), color = Color(0xFF555B6E), fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
        for (i in 0 until WHITE_COUNT - 1) {
            val wo = WHITE_OFFSETS[i % 7]
            if (wo != 0 && wo != 2 && wo != 5 && wo != 7 && wo != 9) continue
            val midi = baseMidi + (i / 7) * 12 + wo + 1
            Box(
                Modifier
                    .offset(x = ww * (i + 1) - ww * 0.3f)
                    .width(ww * 0.6f)
                    .height(140.dp)
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(if (pressed[midi] == true) color else Color(0xFF14161D))
                    .border(1.dp, Color(0xFF3A3F50), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .keyPress(midi, inst, {
                        pressed[midi] = true
                        Preview.playNote(inst, midi)
                        studio.keyDown(midi)
                    }, {
                        pressed[midi] = false
                        studio.keyUp(midi)
                    })
            )
        }
    }
}

@Composable
private fun Pads(studio: Studio) {
    val pressed = remember { mutableStateMapOf<String, Boolean>() }
    val color = instInfo("drums").color
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DRUM_NAMES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { name ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (pressed[name] == true) color else Surface2)
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .keyPress(name, "drums", {
                                pressed[name] = true
                                Preview.playDrum(name)
                                studio.keyDown(DRUM_NAMES.indexOf(name))
                            }, {
                                pressed[name] = false
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            DRUM_LABELS[name] ?: name, color = TextHi, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
