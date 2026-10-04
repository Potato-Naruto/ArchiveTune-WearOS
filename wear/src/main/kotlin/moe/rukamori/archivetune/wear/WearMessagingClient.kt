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

/** Sends requests to the phone app over the Wearable Data Layer. */
class WearMessagingClient(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val capabilityClient by lazy { Wearable.getCapabilityClient(appContext) }
    private val nodeClient by lazy { Wearable.getNodeClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }

    /**
     * Sends [path] with [payload] to the phone.
     *
     * @return false when no phone is connected or the send failed. True only means the message
     * was handed over, not that the phone app acted on it.
     */
    suspend fun send(
        path: String,
        payload: ByteArray = EMPTY_PAYLOAD,
    ): Boolean =
        try {
            val nodeId = phoneNodeId()
            if (nodeId == null) {
                Log.w(TAG, "No connected phone to send $path to")
                false
            } else {
                messageClient.sendMessage(nodeId, path, payload).await()
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

    /**
     * Prefers a phone advertising [WearProtocol.PHONE_CAPABILITY]. Capabilities sync separately
     * from the connection itself and can lag behind an install — or never arrive, as between two
     * emulators whose Play services are signed differently — so any connected node is the
     * fallback. A message to a node without the phone app is simply not delivered.
     */
    private suspend fun phoneNodeId(): String? {
        val capable =
            capabilityClient
                .getCapability(WearProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        val node =
            capable.firstOrNull { it.isNearby }
                ?: capable.firstOrNull()
                ?: nodeClient.connectedNodes.await().let { nodes ->
                    nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                }
        return node?.id
    }

    private companion object {
        const val TAG = "WearMessaging"
        val EMPTY_PAYLOAD = ByteArray(0)
    }
}
