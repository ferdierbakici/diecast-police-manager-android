package app.diecast.police.museum.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MuseumError = Color(0xFFB3261E)

private val MuseumColorScheme = lightColorScheme(
    primary = MuseumGold,
    onPrimary = MuseumSurface,
    primaryContainer = MuseumGoldSoft,
    onPrimaryContainer = MuseumInk,
    secondary = MuseumInk,
    onSecondary = MuseumSurface,
    secondaryContainer = MuseumCreamDark,
    onSecondaryContainer = MuseumInk,
    tertiary = MuseumInkSoft,
    onTertiary = MuseumSurface,
    background = MuseumCream,
    onBackground = MuseumInk,
    surface = MuseumSurface,
    onSurface = MuseumInk,
    surfaceVariant = MuseumCreamDark,
    onSurfaceVariant = MuseumInkSoft,
    outline = MuseumBorder,
    outlineVariant = MuseumBorder,
    error = MuseumError,
    onError = MuseumSurface
)

@Composable
fun DiecastMuseumTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // The museum palette is fixed: a dark theme is deliberately not implemented (the site uses a fixed palette too).
    @Suppress("UNUSED_EXPRESSION")
    darkTheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = MuseumCream.toArgb()
            window.navigationBarColor = MuseumCream.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = MuseumColorScheme,
        typography = MuseumTypography,
        content = content
    )
}
