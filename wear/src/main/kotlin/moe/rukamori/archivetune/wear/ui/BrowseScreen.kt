/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.MediaEntry
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel

/**
 * One folder of the phone's library tree. A folder that can itself be played — a playlist, an
 * album — gets Play and Shuffle above its songs.
 */
@Composable
fun BrowseScreen(
    viewModel: RemoteViewModel,
    parentId: String,
    title: String,
    playable: Boolean,
    onBrowse: (MediaEntry) -> Unit,
    onPlayed: () -> Unit,
) {
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    LaunchedEffect(parentId) { viewModel.loadChildren(parentId) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Text(title.trim(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            if (playable) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        FilledIconButton(
                            onClick = {
                                viewModel.playItem(parentId)
                                onPlayed()
                            },
                        ) {
                            Icon(painterResource(R.drawable.play), stringResource(R.string.play))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.playItem(parentId, shuffle = true)
                                onPlayed()
                            },
                        ) {
                            Icon(painterResource(R.drawable.shuffle), stringResource(R.string.shuffle))
                        }
                    }
                }
            }
            mediaEntries(
                state = browse[parentId],
                onRetry = { viewModel.loadChildren(parentId, force = true) },
                icon = { if (it.browsable) R.drawable.library_music else R.drawable.music_note },
                onClick = { entry ->
                    if (entry.browsable) {
                        onBrowse(entry)
                    } else {
                        viewModel.playItem(entry.id)
                        onPlayed()
                    }
                },
            )
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: RemoteViewModel,
    query: String,
    onPlayed: () -> Unit,
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    LaunchedEffect(query) { viewModel.loadSearch(query) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Text("“$query”", maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.playSearch(query)
                        onPlayed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.play), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.play_all_results))
                }
            }
            mediaEntries(
                state = search[query],
                onRetry = { viewModel.loadSearch(query, force = true) },
                icon = { R.drawable.music_note },
                onClick = { entry ->
                    viewModel.playItem(entry.id)
                    onPlayed()
                },
            )
        }
    }
}
