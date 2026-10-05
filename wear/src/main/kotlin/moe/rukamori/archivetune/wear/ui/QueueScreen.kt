/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel

/**
 * The phone's queue as one list, scrolled so the playing song sits in the middle: what already
 * played above it, what is coming below. Tapping a row jumps there.
 */
@Composable
fun QueueScreen(viewModel: RemoteViewModel) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val position = queue.currentPosition
    LaunchedEffect(Unit) { viewModel.loadQueue() }
    // +1 for the header row. Keyed on the id too, so a new queue that lands on the same position
    // still recentres.
    LaunchedEffect(queue.currentId, position) {
        if (position >= 0) listState.animateScrollToItem(position + 1)
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.queue)) } }
            if (queue.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }
            items(queue.items, key = { it.id }) { entry ->
                val index = queue.items.indexOf(entry)
                val playing = index == position
                val played = position >= 0 && index < position
                Button(
                    onClick = { viewModel.playQueueItem(entry.id) },
                    modifier = Modifier.fillMaxWidth().alpha(if (played) 0.55f else 1f),
                    colors = if (playing) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    icon =
                        if (playing) {
                            { Icon(painterResource(R.drawable.play), contentDescription = null) }
                        } else {
                            null
                        },
                    secondaryLabel =
                        entry.artist.takeIf { it.isNotBlank() }?.let { artist ->
                            { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                ) {
                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
