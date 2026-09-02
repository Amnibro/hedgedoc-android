package org.hedgedoc.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun ScientPalette.scheme() = if (lightBars) {
    lightColorScheme(
        primary = accent,
        onPrimary = accentInk,
        primaryContainer = panel2,
        onPrimaryContainer = text,
        secondary = accentSoft,
        onSecondary = accentInk,
        tertiary = accent,
        background = bg,
        onBackground = text,
        surface = panel,
        onSurface = text,
        surfaceVariant = panel2,
        onSurfaceVariant = textSoft,
        outline = border,
        error = danger,
        onError = accentInk,
        inverseSurface = paper,
        inverseOnSurface = paperInk,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = accentInk,
        primaryContainer = panel2,
        onPrimaryContainer = text,
        secondary = accentSoft,
        onSecondary = accentInk,
        tertiary = accent,
        background = bg,
        onBackground = text,
        surface = panel,
        onSurface = text,
        surfaceVariant = panel2,
        onSurfaceVariant = textSoft,
        outline = border,
        error = danger,
        onError = accentInk,
        inverseSurface = paper,
        inverseOnSurface = paperInk,
    )
}

@Composable
fun HedgeTheme(
    themeId: String = "scient",
    content: @Composable () -> Unit,
) {
    val palette = ScientPalettes.byId(themeId)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = palette.bg.toArgb()
                window.navigationBarColor = palette.bg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = palette.lightBars
            }
        }
    }
    CompositionLocalProvider(LocalScient provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme(),
            typography = HedgeTypography,
            content = content,
        )
    }
}
