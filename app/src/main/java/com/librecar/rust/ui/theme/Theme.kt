package com.librecar.rust.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AccentPreset(val id: String, val labelEn: String, val labelPl: String, val primary: Color, val secondary: Color) {
    MATERIAL_YOU("dynamic", "Material You (Wallpaper)", "Material You (Tapeta)", Color(0xFF00E676), Color(0xFF40C4FF)),
    ECO_EMERALD("emerald", "Eco Emerald", "Eko Szmaragd", Color(0xFF00E676), Color(0xFF69F0AE)),
    ELECTRIC_CYAN("cyan", "Electric Cyan", "Elektryczny Cyjan", Color(0xFF00E5FF), Color(0xFF84FFFF)),
    SPORT_CRIMSON("crimson", "Sport Crimson", "Sportowa Czerwień", Color(0xFFFF5252), Color(0xFFFF8A80)),
    AMBER_NIGHT("amber", "Amber Night", "Bursztynowa Noc", Color(0xFFFFAB40), Color(0xFFFFD180)),
    VIOLET_PULSE("violet", "Violet Pulse", "Fioletowy Puls", Color(0xFFB388FF), Color(0xFFEA80FC));

    companion object {
        fun fromId(id: String): AccentPreset = entries.firstOrNull { it.id == id } ?: MATERIAL_YOU
    }
}

val PureBlack = Color(0xFF000000)
val CardBlack = Color(0xFF0A0A0D)
val ElevatedBlack = Color(0xFF131318)
val HairlineBorder = Color(0xFF23232C)
val EcoGreen = Color(0xFF00E676)
val EcoAmber = Color(0xFFFFB300)
val AlertRed = Color(0xFFFF5252)

private fun buildOledBlackScheme(base: ColorScheme, preset: AccentPreset): ColorScheme {
    val primary = if (preset == AccentPreset.MATERIAL_YOU) base.primary else preset.primary
    val secondary = if (preset == AccentPreset.MATERIAL_YOU) base.secondary else preset.secondary
    val tertiary = if (preset == AccentPreset.MATERIAL_YOU) base.tertiary else EcoGreen
    return base.copy(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.22f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = secondary.copy(alpha = 0.20f),
        onSecondaryContainer = secondary,
        tertiary = tertiary,
        background = PureBlack,
        onBackground = Color(0xFFF3F4F6),
        surface = PureBlack,
        onSurface = Color(0xFFF3F4F6),
        surfaceVariant = ElevatedBlack,
        onSurfaceVariant = Color(0xFFA1A1AA),
        surfaceContainerLowest = PureBlack,
        surfaceContainerLow = CardBlack,
        surfaceContainer = CardBlack,
        surfaceContainerHigh = ElevatedBlack,
        surfaceContainerHighest = Color(0xFF1B1B22),
        outline = HairlineBorder,
        outlineVariant = Color(0xFF1C1C24),
        error = AlertRed,
    )
}

private val CarTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.0).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LibreCarTheme(
    accentPreset: AccentPreset = AccentPreset.MATERIAL_YOU,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    val oledScheme = buildOledBlackScheme(baseScheme, accentPreset)

    MaterialTheme(
        colorScheme = oledScheme,
        typography = CarTypography,
        content = content,
    )
}
