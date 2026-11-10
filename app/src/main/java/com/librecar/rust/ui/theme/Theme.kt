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
    MATERIAL_YOU("dynamic", "System", "Systemowy", Color(0xFFFFFFFF), Color(0xFFA1A1AA)),
    MONOCHROME("mono", "Pure White", "Monochromatyczny", Color(0xFFFFFFFF), Color(0xFFD4D4D8)),
    ECO_EMERALD("emerald", "Eco Green", "Eko Zieleń", Color(0xFF22C55E), Color(0xFF86EFAC)),
    ELECTRIC_CYAN("cyan", "Ice Blue", "Błękit", Color(0xFF38BDF8), Color(0xFF7DD3FC)),
    SPORT_CRIMSON("crimson", "Track Red", "Czerwień", Color(0xFFEF4444), Color(0xFFFCA5A5)),
    AMBER_NIGHT("amber", "Amber", "Bursztyn", Color(0xFFF59E0B), Color(0xFFFCD34D));

    companion object {
        fun fromId(id: String): AccentPreset = entries.firstOrNull { it.id == id } ?: MATERIAL_YOU
    }
}

val PureBlack = Color(0xFF000000)
val CardBlack = Color(0xFF0C0C0E)
val ElevatedBlack = Color(0xFF15151A)
val HairlineBorder = Color(0xFF23232A)
val MutedText = Color(0xFF8E8E93)
val EcoGreen = Color(0xFF22C55E)
val EcoAmber = Color(0xFFF59E0B)
val AlertRed = Color(0xFFEF4444)

private fun buildOledBlackScheme(base: ColorScheme, preset: AccentPreset): ColorScheme {
    val primary = if (preset == AccentPreset.MATERIAL_YOU) base.primary else preset.primary
    val secondary = if (preset == AccentPreset.MATERIAL_YOU) base.secondary else preset.secondary
    val tertiary = if (preset == AccentPreset.MATERIAL_YOU) base.tertiary else EcoGreen
    return base.copy(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.16f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = secondary.copy(alpha = 0.14f),
        onSecondaryContainer = secondary,
        tertiary = tertiary,
        background = PureBlack,
        onBackground = Color(0xFFF5F5F7),
        surface = PureBlack,
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = ElevatedBlack,
        onSurfaceVariant = MutedText,
        surfaceContainerLowest = PureBlack,
        surfaceContainerLow = CardBlack,
        surfaceContainer = CardBlack,
        surfaceContainerHigh = ElevatedBlack,
        surfaceContainerHighest = Color(0xFF1C1C22),
        outline = HairlineBorder,
        outlineVariant = HairlineBorder,
        error = AlertRed,
    )
}

private val CarTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
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
