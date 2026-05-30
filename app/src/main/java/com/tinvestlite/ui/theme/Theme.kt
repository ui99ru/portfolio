package com.tinvestlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BrandYellow,
    onPrimary = Color(0xFF15171C),
    secondary = BrandYellowDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceMuted,
    error = LossRed,
)

// Light theme intentionally maps to the same dark palette: this is a
// minimalist, dark-only client by design.
@Composable
fun TInvestLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
    ) {
        // Root Surface sets the background and, crucially, the default
        // content color (onBackground) so screens that aren't wrapped in a
        // Scaffold still render readable text instead of the M3 default black.
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
