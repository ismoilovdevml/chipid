package com.ismoilovdev.chipid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.R

/** Palette from the ChipID design (dark only). */
object C {
    val bg = Color(0xFF0E1014)
    val scanBg = Color(0xFF07080A)
    val surface = Color(0xFF171A20)
    val surface2 = Color(0xFF15181D)
    val control = Color(0xFF1A1D23)
    val border = Color(0xFF262A33)
    val fieldBorder = Color(0xFF2D3440)
    val divider = Color(0xFF23272F)
    val outline = Color(0xFF3A404B)
    val primary = Color(0xFF7DB4FF)
    val onPrimary = Color(0xFF0B1A2E)
    val selectedBg = Color(0xFF16202E)
    val iconBg = Color(0xFF22324A)
    val iconBgMuted = Color(0xFF20242C)
    val track = Color(0xFF2A2F38)
    val track2 = Color(0xFF20242C)
    val text = Color(0xFFF2F4F7)
    val text2 = Color(0xFFC3CAD5)
    val muted = Color(0xFF9AA3B2)
    val faint = Color(0xFF6B7380)
    val success = Color(0xFF4ADE9B)
    val successBg = Color(0xFF12261C)
    val successBorder = Color(0xFF1F4A33)
    val successText = Color(0xFFA7D9BF)
    val warn = Color(0xFFF5B24B)
    val warnBg = Color(0xFF2A2112)
    val warnBorder = Color(0xFF4A3A1C)
    val warnText = Color(0xFFE9CF9E)
    val danger = Color(0xFFF87171)
    val dangerBg = Color(0xFF2A1416)
    val dangerBorder = Color(0xFF4E2328)
    val dangerText = Color(0xFFF1B5B5)
    val paper = Color(0xFF2A2620)
    val paperInk = Color(0xFF4A443A)
    val ring1 = Color(0xFF1F2A3A)
    val ring2 = Color(0xFF2A3B55)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(ExperimentalTextApi::class)
private fun mono(w: Int) = Font(R.font.jetbrains_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
val Mono = FontFamily(mono(400), mono(500), mono(700))

object T {
    val h1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em, color = C.text)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em, color = C.text)
    val h3 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, color = C.text)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = C.text)
    val nav = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, color = C.text)
    val bodyMuted = body.copy(color = C.muted)
    val small = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp, color = C.muted)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp, color = C.muted)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = C.text2)
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.06.em, color = C.muted)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = C.text)
    val monoSmall = mono.copy(fontSize = 13.sp)
}

@Composable
fun ChipIdTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.primary, onPrimary = C.onPrimary, background = C.bg, onBackground = C.text,
        surface = C.surface, onSurface = C.text, onSurfaceVariant = C.muted, error = C.danger, outline = C.fieldBorder,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = T.body), content = content)
}
