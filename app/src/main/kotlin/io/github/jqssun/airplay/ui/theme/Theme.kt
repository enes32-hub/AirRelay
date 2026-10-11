package io.github.jqssun.airplay.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.receiver.AppearancePolicy

data class ReceiverPalette(val Background: Color, val Panel: Color, val Elevated: Color,
    val Accent: Color, val Text: Color, val Muted: Color, val Border: Color,
    val AccentSoft: Color, val Danger: Color, val Warning: Color, val OnAccent: Color,
    val Focus: Color = Text, val Hover: Color = Accent, val Link: Color = Accent,
    val Success: Color = Accent, val SelectedFill: Color = AccentSoft,
    val SelectedInk: Color = Accent, val ButtonSurface: Color = Panel,
    /** Navigation rail surface: slightly apart from [Background] so the rail reads as its own region. */
    val Rail: Color = Background)

// One brand palette family for every theme: the same indigo accent and the same semantic roles,
// with only the surface tones changing. Previously the light theme carried a green accent, the dark
// theme a neutral grey one and the playback surface a third green, so switching themes looked like
// switching applications.
private val classic = ReceiverPalette(Color(0xFF17191B), Color(0xFF222629), Color(0xFF2B3033),
    Color(0xFFC7C4F8), Color(0xFFF3F2EE), Color(0xFFA5ADB1), Color(0xFF3B4145),
    Color(0xFF343A5C), Color(0xFFE9ACA3), Color(0xFFE6CCA2), Color(0xFF222335),
    Focus = Color(0xFFF0EEE5), Hover = Color(0xFFE1DFFB), Link = Color(0xFFC7C4F8),
    Success = Color(0xFFAED5BA), SelectedFill = Color(0xFF3A3F66),
    SelectedInk = Color(0xFFF3F2EE), ButtonSurface = Color(0xFF222629),
    Rail = Color(0xFF121416))

// Light counterpart of `classic`: identical roles and accent hue, surfaces inverted. The accent is
// the same indigo darkened until it carries enough contrast for text and icons on white.
private val light = ReceiverPalette(Color(0xFFF7F7F5), Color(0xFFFFFFFF), Color(0xFFECEDEA),
    Color(0xFF4A46A8), Color(0xFF1B1D22), Color(0xFF5B6169), Color(0xFFC9CBC7),
    Color(0xFFE7E5F6), Color(0xFFA92724), Color(0xFF825B0A), Color(0xFFFFFFFF),
    Focus = Color(0xFF4A46A8), Hover = Color(0xFFDAD7F1), Link = Color(0xFF4A46A8),
    Success = Color(0xFF2F6B4C), SelectedFill = Color(0xFFDCD9F3),
    SelectedInk = Color(0xFF2C2A63), ButtonSurface = Color(0xFFFFFFFF),
    Rail = Color(0xFFEFEFEC))

val LocalReceiverPalette = staticCompositionLocalOf { classic }
// Media surfaces: the `classic` family, deepened so the picture sits on a calmer background.
private val playback = ReceiverPalette(
    Color(0xFF0E1113), Color(0xFF1A1E21), Color(0xFF262B2E),
    Color(0xFFC7C4F8), Color(0xFFF3F2EE), Color(0xFF9BA3A8), Color(0xFF343A3E),
    Color(0xFF2F3560), Color(0xFFE9ACA3), Color(0xFFE6CCA2), Color(0xFF1B1D2A),
    Focus = Color(0xFFF0EEE5), Hover = Color(0xFFE1DFFB), Link = Color(0xFFC7C4F8),
    Success = Color(0xFFAED5BA), SelectedFill = Color(0xFF363C63),
    SelectedInk = Color(0xFFF3F2EE), ButtonSurface = Color(0xFF1A1E21),
    Rail = Color(0xFF0A0D0F))

/** Playback-only colors; inherit the existing typography, shapes and sizing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackDialogColors(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    CompositionLocalProvider(LocalReceiverPalette provides playback) {
        MaterialTheme(colorScheme = scheme(playback, false)) {
            CompositionLocalProvider(LocalContentColor provides playback.Text,
                LocalRippleConfiguration provides null, content = content)
        }
    }
}
object AirRelayColors {
    val Focus: Color @Composable get() = LocalReceiverPalette.current.Focus
    val Hover: Color @Composable get() = LocalReceiverPalette.current.Hover
    val Link: Color @Composable get() = LocalReceiverPalette.current.Link
    val Success: Color @Composable get() = LocalReceiverPalette.current.Success
    val SelectedFill: Color @Composable get() = LocalReceiverPalette.current.SelectedFill
    val SelectedInk: Color @Composable get() = LocalReceiverPalette.current.SelectedInk
    val ButtonSurface: Color @Composable get() = LocalReceiverPalette.current.ButtonSurface
    val Background: Color @Composable get() = LocalReceiverPalette.current.Background
    val Panel: Color @Composable get() = LocalReceiverPalette.current.Panel
    val Elevated: Color @Composable get() = LocalReceiverPalette.current.Elevated
    val Accent: Color @Composable get() = LocalReceiverPalette.current.Accent
    /** Ink that stays readable on top of [Accent] and [Focus]: dark in the dark palette, white in the light one. */
    val OnAccent: Color @Composable get() = LocalReceiverPalette.current.OnAccent
    val Rail: Color @Composable get() = LocalReceiverPalette.current.Rail
    val Text: Color @Composable get() = LocalReceiverPalette.current.Text
    val Muted: Color @Composable get() = LocalReceiverPalette.current.Muted
    val Border: Color @Composable get() = LocalReceiverPalette.current.Border
    val AccentSoft: Color @Composable get() = LocalReceiverPalette.current.AccentSoft
    val Danger: Color @Composable get() = LocalReceiverPalette.current.Danger
    val Warning: Color @Composable get() = LocalReceiverPalette.current.Warning
}
private fun scheme(p: ReceiverPalette, white: Boolean): ColorScheme {
    val base = if (white) lightColorScheme() else darkColorScheme()
    return base.copy(primary=p.Accent, onPrimary=p.OnAccent,
        primaryContainer=p.AccentSoft, onPrimaryContainer=p.Accent,
        secondary=p.Muted, onSecondary=p.Background, secondaryContainer=p.Elevated, onSecondaryContainer=p.Text,
        tertiary=p.Accent, onTertiary=p.OnAccent, tertiaryContainer=p.AccentSoft, onTertiaryContainer=p.Text,
        background=p.Background, onBackground=p.Text, surface=p.Panel, onSurface=p.Text,
        surfaceVariant=p.Elevated, onSurfaceVariant=p.Muted,
        surfaceDim=p.Background, surfaceBright=p.Elevated,
        surfaceContainerLowest=p.Background, surfaceContainerLow=p.Panel, surfaceContainer=p.Panel,
        surfaceContainerHigh=p.Elevated, surfaceContainerHighest=p.Elevated, surfaceTint=p.Accent,
        outline=p.Border, outlineVariant=p.Border, error=p.Danger)
}
private val type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 1.sp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirPlayTheme(selected: String = "classic", content: @Composable () -> Unit) {
    val effective = AppearancePolicy.resolve(selected)
    val colors = if (effective == "light") light else classic
    val material = remember(colors) { scheme(colors, effective == "light") }
    CompositionLocalProvider(LocalReceiverPalette provides colors) {
        MaterialTheme(colorScheme = material, typography = type) {
            CompositionLocalProvider(LocalContentColor provides colors.Text,
                LocalRippleConfiguration provides null, content = content)
        }
    }
}
