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

/** The stickers and backdrop a theme draws; see `ui/ThemeDecor.kt`. */
enum class Decor { NONE, SAKURA, NARUTO, SASUKE, SHIKAMARU, SUNSET, NIGHT_SKY, COASTAL }

/**
 * @property showArt whether the album cover fills the player's background. AMOLED turns it off:
 * the point of that theme is that unlit pixels stay unlit.
 * @property scrimAlpha how much black sits between the cover and the controls, unless the user has
 * set their own in Settings.
 */
enum class WearTheme(
    val key: String,
    @StringRes val label: Int,
    val showArt: Boolean = true,
    val grayscaleArt: Boolean = false,
    val scrimAlpha: Float = 0.5f,
    val decor: Decor = Decor.NONE,
) {
    MATERIAL("material", R.string.theme_material),
    AMOLED("amoled", R.string.theme_amoled, showArt = false),
    MONOCHROME("monochrome", R.string.theme_monochrome, grayscaleArt = true, scrimAlpha = 0.6f),
    GRAPHITE("graphite", R.string.theme_graphite, grayscaleArt = true, scrimAlpha = 0.72f),
    SAKURA("sakura", R.string.theme_sakura, decor = Decor.SAKURA),
    NARUTO("naruto", R.string.theme_naruto, decor = Decor.NARUTO),
    SASUKE("sasuke", R.string.theme_sasuke, decor = Decor.SASUKE),
    SHIKAMARU("shikamaru", R.string.theme_shikamaru, decor = Decor.SHIKAMARU),
    SUNSET("sunset", R.string.theme_sunset, decor = Decor.SUNSET),
    NIGHT_SKY("night_sky", R.string.theme_night_sky, decor = Decor.NIGHT_SKY),
    COASTAL("coastal", R.string.theme_coastal, decor = Decor.COASTAL),
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

/**
 * A scheme from three accents and a tinted surface ramp. The themes below differ only in those,
 * so each is one call instead of another twenty-line literal.
 */
private fun accentScheme(
    primary: Long,
    onPrimary: Long,
    primaryContainer: Long,
    secondary: Long,
    tertiary: Long,
    surfaceLow: Long,
    surface: Long,
    surfaceHigh: Long,
    onSurfaceVariant: Long,
): ColorScheme =
    ColorScheme(
        primary = Color(primary),
        primaryDim = Color(primary).copy(alpha = 0.82f),
        primaryContainer = Color(primaryContainer),
        onPrimary = Color(onPrimary),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(secondary),
        secondaryDim = Color(secondary).copy(alpha = 0.82f),
        secondaryContainer = Color(surfaceHigh),
        onSecondary = Color(onPrimary),
        onSecondaryContainer = Color(0xFFF2F2F2),
        tertiary = Color(tertiary),
        tertiaryDim = Color(tertiary).copy(alpha = 0.82f),
        tertiaryContainer = Color(surface),
        onTertiary = Color(onPrimary),
        onTertiaryContainer = Color(0xFFF2F2F2),
        surfaceContainerLow = Color(surfaceLow),
        surfaceContainer = Color(surface),
        surfaceContainerHigh = Color(surfaceHigh),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(onSurfaceVariant),
        outline = Color(onSurfaceVariant).copy(alpha = 0.7f),
        outlineVariant = Color(surfaceHigh),
    )

private val SakuraColors =
    accentScheme(0xFFFFB7C5, 0xFF4A1826, 0xFF7A3049, 0xFFF8C8DC, 0xFFA8D8B0, 0xFF22151B, 0xFF2E1C24, 0xFF3C2530, 0xFFE2B9C6)

private val NarutoColors =
    accentScheme(0xFFFF8A1F, 0xFF2B1300, 0xFF7A3A00, 0xFF6FA8FF, 0xFFFFD54F, 0xFF1D1610, 0xFF292018, 0xFF362A20, 0xFFE0C4A8)

private val SasukeColors =
    accentScheme(0xFF9FB4FF, 0xFF0E1440, 0xFF2F3578, 0xFFB388FF, 0xFFFF6B6B, 0xFF121420, 0xFF1A1D2E, 0xFF25293F, 0xFFB7BEDC)

private val ShikamaruColors =
    accentScheme(0xFFA8C686, 0xFF1A2A0C, 0xFF45602E, 0xFF9CCBEA, 0xFFE8E0C8, 0xFF141A14, 0xFF1D251D, 0xFF283228, 0xFFBCCBB6)

private val SunsetColors =
    accentScheme(0xFFFFAB76, 0xFF3A1708, 0xFF8A3D2E, 0xFFFF8FB8, 0xFFFFD56B, 0xFF21151A, 0xFF2D1C24, 0xFF3B2530, 0xFFE8C2B4)

private val NightSkyColors =
    accentScheme(0xFF9FB8FF, 0xFF0A1238, 0xFF27336E, 0xFFC3B1FF, 0xFFFFE9A8, 0xFF0E1324, 0xFF161C33, 0xFF1F2745, 0xFFB4BEDD)

private val CoastalColors =
    accentScheme(0xFF6FD3C7, 0xFF02302B, 0xFF1F5C63, 0xFF8EC9F0, 0xFFF6D9A8, 0xFF0F1D20, 0xFF16292D, 0xFF1F383D, 0xFFB2D4D3)

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
                WearTheme.SAKURA -> SakuraColors
                WearTheme.NARUTO -> NarutoColors
                WearTheme.SASUKE -> SasukeColors
                WearTheme.SHIKAMARU -> ShikamaruColors
                WearTheme.SUNSET -> SunsetColors
                WearTheme.NIGHT_SKY -> NightSkyColors
                WearTheme.COASTAL -> CoastalColors
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
