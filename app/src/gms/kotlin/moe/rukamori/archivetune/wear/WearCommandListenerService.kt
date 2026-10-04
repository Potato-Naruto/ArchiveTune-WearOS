/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Receives the Wear OS remote's messages and hands them to [WearBridge]. */
class WearCommandListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        when (path) {
            in READ_PATHS -> WearBridge.launch(this, messageEvent.sourceNodeId, path, messageEvent.data)

            WearProtocol.PATH_SYNC -> WearPlaylistSyncWorker.enqueue(this, messageEvent.sourceNodeId)

            in COMMAND_PATHS -> {
                // Blocks the listener thread on purpose: Play services keeps this service bound,
                // and the process alive, only until the callback returns, and starting a search
                // or a playlist needs the session connection to outlive its lookup.
                runBlocking {
                    val handled =
                        withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                            WearBridge.handle(
                                this@WearCommandListenerService,
                                messageEvent.sourceNodeId,
                                path,
                                messageEvent.data,
                            )
                        }
                    if (handled == null) Timber.tag(TAG).w("Wear command %s timed out", path)
                }
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private companion object {
        const val TAG = "WearCommand"

        val COMMAND_PATHS =
            setOf(
                WearProtocol.PATH_PLAY,
                WearProtocol.PATH_PAUSE,
                WearProtocol.PATH_SKIP_NEXT,
                WearProtocol.PATH_SKIP_PREV,
                WearProtocol.PATH_TOGGLE_PLAY,
                WearProtocol.PATH_TOGGLE_SHUFFLE,
                WearProtocol.PATH_REPEAT,
                WearProtocol.PATH_VOLUME,
                WearProtocol.PATH_SEEK,
                WearProtocol.PATH_SEARCH_VOICE,
                WearProtocol.PATH_PLAY_ITEM,
            )

        // Lookups can take seconds; run behind the listener thread so a Pause sent meanwhile is
        // not stuck waiting for search results.
        val READ_PATHS = setOf(WearProtocol.PATH_STATE, WearProtocol.PATH_BROWSE, WearProtocol.PATH_SEARCH)

        const val COMMAND_TIMEOUT_MS = 30_000L
    }
}
