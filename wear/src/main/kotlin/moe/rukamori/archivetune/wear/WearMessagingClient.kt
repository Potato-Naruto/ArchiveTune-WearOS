/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Sends playback commands to the phone app over the Wearable Data Layer. */
class WearMessagingClient(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val capabilityClient by lazy { Wearable.getCapabilityClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }

    /**
     * Sends [path] with [payload] to a phone advertising [PHONE_CAPABILITY].
     *
     * @return false when no such phone is reachable or the send failed. True only means the message
     * was handed to the phone, not that playback changed.
     */
    suspend fun send(
        path: String,
        payload: ByteArray = EMPTY_PAYLOAD,
    ): Boolean =
        try {
            val nodes =
                capabilityClient
                    .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (node == null) {
                Log.w(TAG, "No reachable node advertises $PHONE_CAPABILITY")
                false
            } else {
                messageClient.sendMessage(node.id, path, payload).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sending $path failed", e)
            false
        }

    suspend fun send(
        path: String,
        text: String,
    ): Boolean = send(path, text.toByteArray(Charsets.UTF_8))

    companion object {
        private const val TAG = "WearMessaging"

        // Declared by the phone app in app/src/gms/res/values/wear.xml.
        const val PHONE_CAPABILITY = "archivetune_phone_playback"

        // Mirrored in the phone app's WearCommandListenerService — the two APKs share no code module.
        const val PATH_PLAY = "/play"
        const val PATH_PAUSE = "/pause"
        const val PATH_SKIP_NEXT = "/skip_next"
        const val PATH_SKIP_PREV = "/skip_prev"
        const val PATH_SEARCH_VOICE = "/search_voice"

        private val EMPTY_PAYLOAD = ByteArray(0)
    }
}
