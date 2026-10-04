/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.wear.PlayerState
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.RemoteViewModel
import moe.rukamori.archivetune.wear.WearTheme

// How far the crown has to turn for one volume step.
private const val ROTARY_PIXELS_PER_STEP = 48f

@Composable
fun NowPlayingScreen(
    viewModel: RemoteViewModel,
    onOpenVolume: () -> Unit,
) {
    val state by viewModel.player.collectAsStateWithLifecycle()
    val art by viewModel.art.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val phoneReachable by viewModel.phoneReachable.collectAsStateWithLifecycle()
    val voiceSearch = rememberSpeechInput(onResult = viewModel::playSearch)

    var rotaryAccumulated by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    rotaryAccumulated += event.verticalScrollPixels
                    val steps = (rotaryAccumulated / ROTARY_PIXELS_PER_STEP).toInt()
                    if (steps != 0) {
                        rotaryAccumulated -= steps * ROTARY_PIXELS_PER_STEP
                        viewModel.setVolume(state.volume + steps)
                    }
                    true
                }.requestFocusOnHierarchyActive()
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        ArtBackground(art = art.takeIf { state.hasItem }, theme = theme)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(18.dp))
            val title =
                when {
                    !phoneReachable -> stringResource(R.string.phone_unreachable)
                    state.hasItem -> state.title
                    else -> stringResource(R.string.nothing_playing)
                }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.72f).basicMarquee(),
            )
            Text(
                text =
                    if (phoneReachable && !state.hasItem) {
                        stringResource(R.string.nothing_playing_hint)
                    } else {
                        state.artist.takeIf { phoneReachable }.orEmpty()
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.skip_previous))
                }
                PlayPauseButton(state = state, onClick = viewModel::togglePlay)
                IconButton(onClick = viewModel::skipNext) {
                    Icon(painterResource(R.drawable.skip_next), stringResource(R.string.skip_next))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallAction(
                    icon = R.drawable.shuffle,
                    description = R.string.shuffle,
                    active = state.shuffle,
                    onClick = viewModel::toggleShuffle,
                )
                SmallAction(icon = R.drawable.mic, description = R.string.voice_search, onClick = voiceSearch)
                SmallAction(icon = R.drawable.volume_up, description = R.string.volume, onClick = onOpenVolume)
            }
        }
    }
}

@Composable
private fun ArtBackground(
    art: ImageBitmap?,
    theme: WearTheme,
) {
    if (!theme.showArt) return
    Crossfade(targetState = art, animationSpec = tween(450), label = "art") { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter =
                    if (theme.grayscaleArt) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    // Darker toward the rim, where the title and the small actions sit on a round face.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.Black.copy(alpha = theme.scrimAlpha),
                        Color.Black.copy(alpha = (theme.scrimAlpha + 0.3f).coerceAtMost(1f)),
                    ),
                ),
            ),
    )
}

@Composable
private fun PlayPauseButton(
    state: PlayerState,
    onClick: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
        CircularProgressIndicator(
            progress = {
                if (state.durationMs <= 0) {
                    0f
                } else {
                    (state.positionAt(now).toFloat() / state.durationMs).coerceIn(0f, 1f)
                }
            },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 3.dp,
        )
        FilledIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(if (state.playWhenReady) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(if (state.playWhenReady) R.string.pause else R.string.play),
            )
        }
    }
}

@Composable
private fun SmallAction(
    icon: Int,
    description: Int,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(IconButtonDefaults.SmallButtonSize),
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor =
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            modifier = Modifier.size(IconButtonDefaults.SmallIconSize),
        )
    }
}
