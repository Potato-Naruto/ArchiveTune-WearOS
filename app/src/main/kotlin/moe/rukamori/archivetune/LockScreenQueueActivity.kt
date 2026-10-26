/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import moe.rukamori.archivetune.playback.MusicService

/**
 * A small player that shows over the lock screen: the previous song, the current one and the next
 * one, and tapping either neighbour jumps to it.
 *
 * It is the media notification's content intent (see MusicService), so on an unlocked device it
 * must be invisible — it hands straight over to [MainActivity] — and the queue UI only exists while
 * the keyguard is up. The system's own lock screen player is drawn by SystemUI and only ever shows
 * the current track, which is why this is a screen of ours rather than extra session metadata.
 */
class LockScreenQueueActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var snapshot by mutableStateOf(QueueSnapshot())

    private val playerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                snapshot = QueueSnapshot.of(player)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setShowWhenLocked(true)

        val future =
            MediaController
                .Builder(this, SessionToken(this, ComponentName(this, MusicService::class.java)))
                .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller.addListener(playerListener)
                snapshot = QueueSnapshot.of(controller)
                this.controller = controller
            },
            ContextCompat.getMainExecutor(this),
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LockScreenQueue(
                        snapshot = snapshot,
                        onPrevious = { snapshot.previousIndex.takeIf { it != C.INDEX_UNSET }?.let { controller?.seekTo(it, 0L) } },
                        onNext = { snapshot.nextIndex.takeIf { it != C.INDEX_UNSET }?.let { controller?.seekTo(it, 0L) } },
                        onTogglePlay = {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                        },
                    )
                }
            }
        }
    }

    private var controller: MediaController? = null

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        super.onDestroy()
    }
}

private data class QueueSnapshot(
    val previous: MediaItem? = null,
    val current: MediaItem? = null,
    val next: MediaItem? = null,
    val previousIndex: Int = C.INDEX_UNSET,
    val nextIndex: Int = C.INDEX_UNSET,
    val isPlaying: Boolean = false,
) {
    companion object {
        // previousMediaItemIndex / nextMediaItemIndex already honour shuffle and repeat, so the
        // neighbours shown here are the ones the transport buttons would actually play.
        fun of(player: Player): QueueSnapshot {
            if (player.mediaItemCount == 0) return QueueSnapshot()
            val previousIndex = player.previousMediaItemIndex
            val nextIndex = player.nextMediaItemIndex
            return QueueSnapshot(
                previous = previousIndex.takeIf { it != C.INDEX_UNSET }?.let(player::getMediaItemAt),
                current = player.currentMediaItem,
                next = nextIndex.takeIf { it != C.INDEX_UNSET }?.let(player::getMediaItemAt),
                previousIndex = previousIndex,
                nextIndex = nextIndex,
                isPlaying = player.isPlaying,
            )
        }
    }
}

@Composable
private fun LockScreenQueue(
    snapshot: QueueSnapshot,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        NeighbourRow(
            label = stringResource(R.string.lock_queue_previous),
            item = snapshot.previous,
            onClick = onPrevious,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(item = snapshot.current, size = 240)
            Spacer(Modifier.height(16.dp))
            Text(
                text = snapshot.current?.mediaMetadata?.title?.toString().orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.current?.mediaMetadata?.artist?.toString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = snapshot.previous != null) {
                    Icon(painterResource(R.drawable.skip_previous), contentDescription = stringResource(R.string.lock_queue_previous))
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp)) {
                    Icon(
                        painter = painterResource(if (snapshot.isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = snapshot.next != null) {
                    Icon(painterResource(R.drawable.skip_next), contentDescription = stringResource(R.string.lock_queue_next))
                }
            }
        }

        NeighbourRow(
            label = stringResource(R.string.lock_queue_next),
            item = snapshot.next,
            onClick = onNext,
        )
    }
}

@Composable
private fun NeighbourRow(
    label: String,
    item: MediaItem?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = item != null, onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(item = item, size = 48)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item?.mediaMetadata?.title?.toString() ?: stringResource(R.string.lock_queue_empty),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item?.mediaMetadata?.artist?.toString()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Artwork(
    item: MediaItem?,
    size: Int,
) {
    AsyncImage(
        model = item?.mediaMetadata?.artworkUri?.toString(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size / 8).dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
