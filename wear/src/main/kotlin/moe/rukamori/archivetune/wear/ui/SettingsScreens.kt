/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.SyncState
import moe.rukamori.archivetune.wear.WearTheme

/** The phone's media volume, one step per tap. */
@Composable
fun VolumeScreen(viewModel: RemoteViewModel) {
    val state by viewModel.player.collectAsStateWithLifecycle()
    ScreenScaffold(
        modifier =
            Modifier.volumeRotary(
                volume = state.volume,
                maxVolume = state.maxVolume,
                onVolumeChange = viewModel::setVolume,
            ),
    ) {
        if (state.maxVolume <= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Stepper(
            value = state.volume,
            onValueChange = viewModel::setVolume,
            valueProgression = 0..state.maxVolume,
            decreaseIcon = { Icon(painterResource(R.drawable.remove), stringResource(R.string.volume_down)) },
            increaseIcon = { Icon(painterResource(R.drawable.add), stringResource(R.string.volume_up)) },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter =
                        painterResource(if (state.volume == 0) R.drawable.volume_off else R.drawable.volume_up),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.volume_percent, state.volume * 100 / state.maxVolume),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}

@Composable
fun ThemeScreen(viewModel: RemoteViewModel) {
    val selected by viewModel.theme.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.theme)) } }
            items(WearTheme.entries) { theme ->
                RadioButton(
                    selected = theme == selected,
                    onSelect = { viewModel.setTheme(theme) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(theme.label)) },
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: RemoteViewModel,
    onOpenThemes: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val artDim by viewModel.artDim.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val syncOnOpen by viewModel.syncOnOpen.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val dim = artDim ?: theme.scrimAlpha

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.settings)) } }
            item {
                Button(
                    onClick = onOpenThemes,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.palette), contentDescription = null) },
                    secondaryLabel = { Text(stringResource(theme.label)) },
                ) {
                    Text(stringResource(R.string.theme))
                }
            }

            item { ListHeader { Text(stringResource(R.string.playlists)) } }
            item { SyncPlaylistsButton(viewModel) }
            item {
                SwitchButton(
                    checked = syncOnOpen,
                    onCheckedChange = viewModel::setSyncOnOpen,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sync_on_open)) },
                )
            }

            item { ListHeader { Text(stringResource(R.string.player)) } }
            item {
                Text(
                    text = stringResource(R.string.cover_darkness, (dim * 100).toInt()),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Slider(
                    value = dim.coerceIn(0f, MAX_ART_DIM),
                    onValueChange = { viewModel.setArtDim(it) },
                    valueRange = 0f..MAX_ART_DIM,
                    steps = 8,
                    decreaseIcon = { Icon(painterResource(R.drawable.remove), stringResource(R.string.lighter)) },
                    increaseIcon = { Icon(painterResource(R.drawable.add), stringResource(R.string.darker)) },
                )
            }
            if (artDim != null) {
                item {
                    Button(
                        onClick = { viewModel.setArtDim(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(stringResource(R.string.use_theme_default))
                    }
                }
            }
            item {
                SwitchButton(
                    checked = keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.keep_screen_on)) },
                    secondaryLabel = { Text(stringResource(R.string.keep_screen_on_hint)) },
                )
            }
            item {
                Button(
                    onClick = onOpenVolume,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.volume_up), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.volume))
                }
            }

            item { ListHeader { Text(stringResource(R.string.updates)) } }
            item {
                Button(
                    onClick = onOpenUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.updates))
                }
            }
        }
    }
}

// Past this the cover is close to black and the slider stops being useful.
private const val MAX_ART_DIM = 0.9f

/** Starts a playlist sync on the phone and shows how it went. */
@Composable
fun SyncPlaylistsButton(viewModel: RemoteViewModel) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
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
