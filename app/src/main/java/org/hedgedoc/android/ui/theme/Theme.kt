package org.hedgedoc.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Greenhouse = darkColorScheme(
    primary = Spine,
    onPrimary = NightPane,
    primaryContainer = Vein,
    onPrimaryContainer = Mist,
    secondary = Verdigris,
    onSecondary = NightPane,
    tertiary = Spine,
    background = NightPane,
    onBackground = Mist,
    surface = Frame,
    onSurface = Mist,
    surfaceVariant = Vein,
    onSurfaceVariant = Stake,
    outline = Stake,
    error = Blot,
    onError = Label,
    inverseSurface = Label,
    inverseOnSurface = Ink,
)

@Composable
fun HedgeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.parseColor("#10262B")
                window.navigationBarColor = android.graphics.Color.parseColor("#10262B")
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = Greenhouse,
        typography = HedgeTypography,
        content = content,
    )
}
