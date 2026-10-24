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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import moe.rukamori.archivetune.wear.ArchiveTuneWearTheme
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.WearTheme

/**
 * The always-on display: the normal player, drawn in the monochrome theme on pure black with no
 * cover art. The layout drifts a few dp each minute (`tick`) to spare the panel burn-in.
 */
@Composable
fun AmbientScreen(
    viewModel: RemoteViewModel,
    tick: Int,
) {
    val shift = (tick % 5 - 2) * 2
    ArchiveTuneWearTheme(WearTheme.MONOCHROME) {
        AppScaffold {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().offset(x = shift.dp, y = (-shift).dp)) {
                    NowPlayingScreen(viewModel = viewModel, ambient = true)
                }
            }
        }
    }
}
