package com.example.musicai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0C10)
val Surface1 = Color(0xFF14161D)
val Surface2 = Color(0xFF1C1F29)
val Surface3 = Color(0xFF282C3A)
val Accent = Color(0xFF8B5CF6)
val AccentSoft = Color(0xFFB69CFF)
val Hot = Color(0xFFFF4D6D)
val Mint = Color(0xFF2DD4BF)
val TextHi = Color(0xFFF2F3F7)
val TextLo = Color(0xFF9AA0B2)

class InstInfo(val key: String, val label: String, val emoji: String, val color: Color)

val INSTRUMENT_LIST = listOf(
    InstInfo("keys", "Piano", "🎹", Color(0xFF60A5FA)),
    InstInfo("bass", "Basse", "🎸", Color(0xFFF59E0B)),
    InstInfo("sub", "Sub 808", "🔊", Color(0xFFEF4444)),
    InstInfo("pad", "Nappe", "☁️", Color(0xFFA78BFA)),
    InstInfo("strings", "Cordes", "🎻", Color(0xFFF472B6)),
    InstInfo("lead", "Lead", "⚡", Color(0xFF34D399)),
    InstInfo("pluck", "Pincé", "🎶", Color(0xFFFBBF24)),
    InstInfo("bell", "Cloche", "🔔", Color(0xFF22D3EE)),
    InstInfo("drums", "Batterie", "🥁", Color(0xFFFB7185))
)

fun instInfo(key: String): InstInfo = INSTRUMENT_LIST.firstOrNull { it.key == key } ?: INSTRUMENT_LIST[0]

@Composable
fun MusiquatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF2A1F52),
            onPrimaryContainer = AccentSoft,
            secondary = Mint,
            onSecondary = Color.Black,
            secondaryContainer = Surface3,
            onSecondaryContainer = TextHi,
            background = Bg,
            onBackground = TextHi,
            surface = Surface1,
            onSurface = TextHi,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextLo,
            error = Hot,
            outline = Color(0xFF3A3F50)
        ),
        content = content
    )
}
