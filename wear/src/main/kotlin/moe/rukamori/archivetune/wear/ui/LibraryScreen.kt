/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.MediaEntry
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.SyncState

// The top of the phone's browse tree (MusicService.ROOT).
private const val ROOT_ID = "root"
private const val PLAYLISTS_ID = RemoteViewModel.PLAYLISTS_ID

@Composable
fun LibraryScreen(
    viewModel: RemoteViewModel,
    onOpenThemes: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onBrowse: (MediaEntry) -> Unit,
) {
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val search = rememberSpeechInput(onResult = onOpenSearch)
    val listState = rememberScalingLazyListState()
    val browseAllTitle = stringResource(R.string.browse_all)
    LaunchedEffect(Unit) { viewModel.loadChildren(PLAYLISTS_ID) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.library)) } }
            item {
                Button(
                    onClick = search,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.search))
                }
            }
            item { ListHeader { Text(stringResource(R.string.playlists)) } }
            item {
                Button(
                    onClick = viewModel::syncPlaylists,
                    enabled = sync != SyncState.Syncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    icon = {
                        if (sync == SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.sync), contentDescription = null)
                        }
                    },
                    secondaryLabel =
                        when (val state = sync) {
                            SyncState.Idle -> null
                            SyncState.Syncing -> ({ Text(stringResource(R.string.syncing)) })
                            SyncState.Failed -> ({ Text(stringResource(R.string.sync_failed)) })
                            is SyncState.Done -> ({ Text(stringResource(R.string.sync_done, state.playlists)) })
                        },
                ) {
                    Text(stringResource(R.string.sync_playlists))
                }
            }
            mediaEntries(
                state = browse[PLAYLISTS_ID],
                onRetry = { viewModel.loadChildren(PLAYLISTS_ID, force = true) },
                icon = { R.drawable.library_music },
                onClick = onBrowse,
            )
            item { ListHeader { Text(stringResource(R.string.more)) } }
            item {
                Button(
                    onClick = { onBrowse(MediaEntry(ROOT_ID, browseAllTitle, "", browsable = true, playable = false)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.music_note), contentDescription = null) },
                ) {
                    Text(browseAllTitle)
                }
            }
            item {
                Button(
                    onClick = onOpenThemes,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.palette), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.theme))
                }
            }
        }
    }
}
