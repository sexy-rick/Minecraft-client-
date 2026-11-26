package dev.empyreal.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightPrimary = Color(0xFF2F6B45)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFB4F0C8)
private val LightOnPrimaryContainer = Color(0xFF00210F)
private val LightSecondary = Color(0xFF97631F)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFFFDCBB)
private val LightOnSecondaryContainer = Color(0xFF2E1500)
private val LightTertiary = Color(0xFF3B6470)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFBFE9F7)
private val LightOnTertiaryContainer = Color(0xFF001F28)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightBackground = Color(0xFFF7FBF2)
private val LightOnBackground = Color(0xFF171D18)
private val LightSurface = Color(0xFFF7FBF2)
private val LightOnSurface = Color(0xFF171D18)
private val LightSurfaceVariant = Color(0xFFDCE5D8)
private val LightOnSurfaceVariant = Color(0xFF404943)
private val LightOutline = Color(0xFF707973)
private val LightOutlineVariant = Color(0xFFC0C9BC)

private val DarkPrimary = Color(0xFF8CD6A9)
private val DarkOnPrimary = Color(0xFF00391E)
private val DarkPrimaryContainer = Color(0xFF14512F)
private val DarkOnPrimaryContainer = Color(0xFFB4F0C8)
private val DarkSecondary = Color(0xFFFFB877)
private val DarkOnSecondary = Color(0xFF4A2800)
private val DarkSecondaryContainer = Color(0xFF6B3D0A)
private val DarkOnSecondaryContainer = Color(0xFFFFDCBB)
private val DarkTertiary = Color(0xFFA3CDF0)
private val DarkOnTertiary = Color(0xFF003545)
private val DarkTertiaryContainer = Color(0xFF1F4C5C)
private val DarkOnTertiaryContainer = Color(0xFFBFE9F7)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF0E1310)
private val DarkOnBackground = Color(0xFFDFE4DC)
private val DarkSurface = Color(0xFF0E1310)
private val DarkOnSurface = Color(0xFFDFE4DC)
private val DarkSurfaceVariant = Color(0xFF404943)
private val DarkOnSurfaceVariant = Color(0xFFC0C9BC)
private val DarkOutline = Color(0xFF8A938C)
private val DarkOutlineVariant = Color(0xFF404943)

private val LightSurfContainerLowest = Color(0xFFFFFFFF)
private val LightSurfContainerLow = Color(0xFFF1F6EC)
private val LightSurfContainer = Color(0xFFEBF0E6)
private val LightSurfContainerHigh = Color(0xFFE5EAE1)
private val LightSurfContainerHighest = Color(0xFFDFE4DB)

private val DarkSurfContainerLowest = Color(0xFF090E0B)
private val DarkSurfContainerLow = Color(0xFF161B17)
private val DarkSurfContainer = Color(0xFF1A1F1B)
private val DarkSurfContainerHigh = Color(0xFF242A25)
private val DarkSurfContainerHighest = Color(0xFF2F342F)

val EmpyrealLightScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfContainerLowest,
    surfaceContainerLow = LightSurfContainerLow,
    surfaceContainer = LightSurfContainer,
    surfaceContainerHigh = LightSurfContainerHigh,
    surfaceContainerHighest = LightSurfContainerHighest,
)

val EmpyrealDarkScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfContainerLowest,
    surfaceContainerLow = DarkSurfContainerLow,
    surfaceContainer = DarkSurfContainer,
    surfaceContainerHigh = DarkSurfContainerHigh,
    surfaceContainerHighest = DarkSurfContainerHighest,
)

val EmpyrealShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

val EmpyrealTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
