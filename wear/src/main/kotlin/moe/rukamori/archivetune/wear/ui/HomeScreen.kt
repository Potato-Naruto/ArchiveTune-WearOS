/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.HorizontalPagerScaffold
import moe.rukamori.archivetune.wear.MediaEntry
import moe.rukamori.archivetune.wear.RemoteViewModel

private const val PAGE_PLAYER = 0
private const val PAGE_QUEUE = 1
private const val PAGE_LIBRARY = 2

/** The root of the app: the player, the queue one swipe over, then the library. */
@Composable
fun HomeScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onBrowse: (MediaEntry) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val showPlayer by viewModel.showPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(showPlayer) {
        if (showPlayer > 0) pagerState.scrollToPage(PAGE_PLAYER)
    }

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    PAGE_PLAYER -> NowPlayingScreen(viewModel = viewModel)

                    PAGE_QUEUE -> QueueScreen(viewModel = viewModel)

                    PAGE_LIBRARY ->
                        LibraryScreen(
                            viewModel = viewModel,
                            onOpenSettings = onOpenSettings,
                            onOpenSearch = onOpenSearch,
                            onBrowse = onBrowse,
                        )
                }
            }
        }
    }
}
