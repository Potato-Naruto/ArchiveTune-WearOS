/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.wear.ArchiveTuneWearTheme
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.WearTheme

private const val SHIFT_INTERVAL_MS = 20_000L

// dp offsets, a loop around the centre so no pixel row stays lit for long.
private val SHIFT_PATH =
    listOf(0 to 0, 3 to 0, 3 to 3, 0 to 3, -3 to 3, -3 to 0, -3 to -3, 0 to -3, 3 to -3)

/**
 * The always-on display: the normal player, drawn in the monochrome theme on pure black with no
 * cover art. The layout shifts a few dp every 20 seconds to spare the panel burn-in.
 */
@Composable
fun AmbientScreen(
    viewModel: RemoteViewModel,
    tick: Int,
) {
    // Steps every 20 s through a ring of positions. The system's own ambient callback only fires
    // once a minute, so this timer is what makes the shift finer; `tick` is kept as a nudge so a
    // system update also moves the layout if the CPU slept through the timer.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SHIFT_INTERVAL_MS)
            step++
        }
    }
    val (shiftX, shiftY) = SHIFT_PATH[(step + tick) % SHIFT_PATH.size]
    ArchiveTuneWearTheme(WearTheme.MONOCHROME) {
        AppScaffold {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().offset(x = shiftX.dp, y = shiftY.dp)) {
                    NowPlayingScreen(viewModel = viewModel, ambient = true)
                }
            }
        }
    }
}
