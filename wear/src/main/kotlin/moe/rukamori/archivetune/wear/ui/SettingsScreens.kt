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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.WearTheme

/** The phone's media volume, one step per tap. */
@Composable
fun VolumeScreen(viewModel: RemoteViewModel) {
    val state by viewModel.player.collectAsStateWithLifecycle()
    ScreenScaffold(
        modifier = Modifier.volumeRotary(volume = state.volume, onVolumeChange = viewModel::setVolume),
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
