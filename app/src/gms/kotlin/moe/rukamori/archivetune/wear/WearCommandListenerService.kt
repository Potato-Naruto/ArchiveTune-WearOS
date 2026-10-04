/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.playback.MusicService
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Receives the Wear OS remote's commands and replays them on [MusicService]'s session through a
 * short-lived [MediaController], so the watch goes through the same door as every other controller.
 */
class WearCommandListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        if (path !in COMMAND_PATHS) {
            super.onMessageReceived(messageEvent)
            return
        }
        // Blocks the listener thread on purpose: Play services keeps this service bound, and the
        // process alive, only until the callback returns, and a voice search needs the controller
        // to outlive its network lookup.
        runBlocking {
            withContext(Dispatchers.Main) {
                val handled = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { handleCommand(path, messageEvent.data) }
                if (handled == null) Timber.tag(TAG).w("Wear command %s timed out", path)
            }
        }
    }

    private suspend fun handleCommand(
        path: String,
        payload: ByteArray,
    ) {
        val future =
            MediaController
                .Builder(this, SessionToken(this, ComponentName(this, MusicService::class.java)))
                .buildAsync()
        try {
            val controller = future.await()
            when (path) {
                PATH_PLAY -> {
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
                }

                PATH_PAUSE -> controller.pause()

                PATH_SKIP_NEXT -> controller.seekToNext()

                PATH_SKIP_PREV -> controller.seekToPrevious()

                PATH_SEARCH_VOICE -> {
                    val query = payload.toString(Charsets.UTF_8).trim()
                    if (query.isNotEmpty()) controller.playFromSearch(query)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Wear command %s failed", path)
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    /**
     * Hands the query to `MediaLibrarySessionCallback.onSetMediaItems`, which resolves a
     * `searchQuery` item into a queue. The session drops a controller's queued commands when it
     * disconnects, so this suspends until the placeholder has been replaced (or cleared, when
     * nothing matched) — releasing earlier would lose the prepare/play behind the lookup.
     */
    private suspend fun MediaController.playFromSearch(query: String) {
        val placeholder =
            MediaItem
                .Builder()
                .setRequestMetadata(
                    MediaItem.RequestMetadata
                        .Builder()
                        .setSearchQuery(query)
                        .build(),
                ).build()
        setMediaItem(placeholder)
        prepare()
        play()
        suspendCancellableCoroutine { continuation ->
            addListener(
                object : Player.Listener {
                    override fun onTimelineChanged(
                        timeline: Timeline,
                        reason: Int,
                    ) {
                        if (currentMediaItem?.requestMetadata?.searchQuery == query) return
                        removeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
        }
    }

    private companion object {
        const val TAG = "WearCommand"

        // Mirrored in the :wear module's WearMessagingClient — the two APKs share no code module.
        const val PATH_PLAY = "/play"
        const val PATH_PAUSE = "/pause"
        const val PATH_SKIP_NEXT = "/skip_next"
        const val PATH_SKIP_PREV = "/skip_prev"
        const val PATH_SEARCH_VOICE = "/search_voice"
        val COMMAND_PATHS = setOf(PATH_PLAY, PATH_PAUSE, PATH_SKIP_NEXT, PATH_SKIP_PREV, PATH_SEARCH_VOICE)

        const val COMMAND_TIMEOUT_MS = 30_000L
    }
}
