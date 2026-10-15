package com.example.musicai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val NOTE_NAMES = arrayOf("Do", "Do#", "Ré", "Ré#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")

fun midiName(m: Int): String = NOTE_NAMES[((m % 12) + 12) % 12] + (m / 12 - 1)

fun isBlackKey(m: Int): Boolean = when (((m % 12) + 12) % 12) {
    1, 3, 6, 8, 10 -> true
    else -> false
}

fun fmtTime(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Accent
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextLo,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SmallBtn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Surface3,
    textColor: Color = TextHi,
    enabled: Boolean = true
) {
    Box(
        modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 38.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    color: Color = Accent,
    text: String = "${(value * 100).roundToInt()}%"
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(62.dp), fontSize = 12.sp, color = TextLo)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(34.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Surface3
            )
        )
        Text(text, Modifier.width(46.dp), fontSize = 12.sp, color = TextHi, textAlign = TextAlign.End)
    }
}

@Composable
fun SettingsDialog(studio: Studio, onDismiss: () -> Unit) {
    val s = studio.settings
    var url by remember { mutableStateOf(s.url) }
    var model by remember { mutableStateOf(s.model) }
    var key by remember { mutableStateOf(s.apiKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Serveur IA (Ollama)") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("Adresse") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, label = { Text("Modèle") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("Clé d'API (optionnel)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { s.update(url, model, key); onDismiss() }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
