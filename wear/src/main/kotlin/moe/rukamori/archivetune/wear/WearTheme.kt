/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * @property showArt whether the album cover fills the player's background. AMOLED turns it off:
 * the point of that theme is that unlit pixels stay unlit.
 * @property scrimAlpha how much black sits between the cover and the controls.
 */
enum class WearTheme(
    val key: String,
    @StringRes val label: Int,
    val showArt: Boolean = true,
    val grayscaleArt: Boolean = false,
    val scrimAlpha: Float = 0.42f,
) {
    MATERIAL("material", R.string.theme_material),
    AMOLED("amoled", R.string.theme_amoled, showArt = false),
    MONOCHROME("monochrome", R.string.theme_monochrome, grayscaleArt = true, scrimAlpha = 0.6f),
    GRAPHITE("graphite", R.string.theme_graphite, grayscaleArt = true, scrimAlpha = 0.72f),
    ;

    companion object {
        fun fromKey(key: String?): WearTheme = entries.firstOrNull { it.key == key } ?: MATERIAL
    }
}

private val AmoledColors =
    ColorScheme(
        surfaceContainerLow = Color(0xFF000000),
        surfaceContainer = Color(0xFF0B0B0B),
        surfaceContainerHigh = Color(0xFF141414),
        outline = Color(0xFF5A5A5A),
        outlineVariant = Color(0xFF2A2A2A),
    )

private val MonochromeColors =
    ColorScheme(
        primary = Color(0xFFFFFFFF),
        primaryDim = Color(0xFFD6D6D6),
        primaryContainer = Color(0xFF3A3A3A),
        onPrimary = Color(0xFF000000),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFD0D0D0),
        secondaryDim = Color(0xFFB0B0B0),
        secondaryContainer = Color(0xFF2E2E2E),
        onSecondary = Color(0xFF000000),
        onSecondaryContainer = Color(0xFFEDEDED),
        tertiary = Color(0xFFBDBDBD),
        tertiaryDim = Color(0xFF9E9E9E),
        tertiaryContainer = Color(0xFF262626),
        onTertiary = Color(0xFF000000),
        onTertiaryContainer = Color(0xFFE0E0E0),
        surfaceContainerLow = Color(0xFF141414),
        surfaceContainer = Color(0xFF1F1F1F),
        surfaceContainerHigh = Color(0xFF2B2B2B),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFBDBDBD),
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFF444444),
    )

private val GraphiteColors =
    ColorScheme(
        primary = Color(0xFFC9CED8),
        primaryDim = Color(0xFFA7AEBB),
        primaryContainer = Color(0xFF3B4049),
        onPrimary = Color(0xFF1A1C20),
        onPrimaryContainer = Color(0xFFE3E6EC),
        secondary = Color(0xFF9CA5B4),
        secondaryDim = Color(0xFF7F8898),
        secondaryContainer = Color(0xFF30343B),
        onSecondary = Color(0xFF16181B),
        onSecondaryContainer = Color(0xFFD5D9E0),
        tertiary = Color(0xFF8FB4D9),
        tertiaryDim = Color(0xFF7499BE),
        tertiaryContainer = Color(0xFF263748),
        onTertiary = Color(0xFF0E1C2A),
        onTertiaryContainer = Color(0xFFCFE3F7),
        surfaceContainerLow = Color(0xFF1D1F23),
        surfaceContainer = Color(0xFF26292E),
        surfaceContainerHigh = Color(0xFF31353B),
        onSurface = Color(0xFFE4E6EA),
        onSurfaceVariant = Color(0xFFA9AFB8),
        outline = Color(0xFF747A84),
        outlineVariant = Color(0xFF3C4047),
    )

@Composable
fun ArchiveTuneWearTheme(
    theme: WearTheme,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        remember(theme) {
            when (theme) {
                // The system palette where the watch has one (Wear OS 6), the Material default below it.
                WearTheme.MATERIAL -> dynamicColorScheme(context) ?: ColorScheme()
                WearTheme.AMOLED -> AmoledColors
                WearTheme.MONOCHROME -> MonochromeColors
                WearTheme.GRAPHITE -> GraphiteColors
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
