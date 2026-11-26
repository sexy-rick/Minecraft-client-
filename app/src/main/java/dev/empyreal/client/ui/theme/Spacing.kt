package dev.empyreal.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

val LocalSpacing = compositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable get() = LocalSpacing.current

object EmpyrealMotion {
    const val SpringStiffnessMedium = 380f
    const val SpringDampingMedium = 0.82f
    const val SpringStiffnessSnappy = 900f
    const val SpringDampingSnappy = 0.72f
    const val DurationShort = 180
    const val DurationMedium = 300
    const val DurationLong = 450
}

@Composable
fun EmpyrealTheme(
    useDarkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDarkTheme -> EmpyrealDarkScheme
        else -> EmpyrealLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EmpyrealTypography,
        shapes = EmpyrealShapes,
        content = content,
    )
}
