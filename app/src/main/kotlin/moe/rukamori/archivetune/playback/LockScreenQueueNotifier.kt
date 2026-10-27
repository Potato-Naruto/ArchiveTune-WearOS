/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.playback

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R

/**
 * While the phone is locked, keeps a second notification up that names the previous and next song
 * and carries one button for each. The system's lock screen media card only ever shows the current
 * track, and tapping it always asks for an unlock; the buttons here opt out of that unlock
 * (Android 12+), so a tap jumps straight to that song.
 *
 * It exists only while the keyguard is up: posted when the screen turns off, withdrawn on unlock.
 */
class LockScreenQueueNotifier(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> Player,
    private val trackChanges: Flow<*>,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private var trackJob: Job? = null
    private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> refresh()
                    Intent.ACTION_USER_PRESENT -> cancel()
                }
            }
        }

    fun start() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.lock_queue_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
        trackJob = scope.launch { trackChanges.collect { refresh() } }
    }

    fun stop() {
        trackJob?.cancel()
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        cancel()
    }

    private fun cancel() = notificationManager.cancel(NOTIFICATION_ID)

    private fun refresh() {
        if (keyguardManager?.isKeyguardLocked != true) return cancel()
        val player = player()
        if (player.mediaItemCount == 0 || player.currentMediaItem == null) return cancel()

        val previous = player.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let(player::getMediaItemAt)
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let(player::getMediaItemAt)
        val noneLabel = context.getString(R.string.lock_queue_empty)
        val previousTitle = previous?.mediaMetadata?.title?.toString() ?: noneLabel
        val nextTitle = next?.mediaMetadata?.title?.toString() ?: noneLabel

        val builder =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(player.currentMediaItem?.mediaMetadata?.title)
                .setContentText(player.currentMediaItem?.mediaMetadata?.artist)
                .setStyle(
                    Notification
                        .BigTextStyle()
                        .bigText(
                            context.getString(R.string.lock_queue_previous) + ": " + previousTitle + "\n" +
                                context.getString(R.string.lock_queue_next) + ": " + nextTitle,
                        ),
                ).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)

        if (previous != null) builder.addAction(jumpAction(R.drawable.skip_previous, previousTitle, ACTION_PREVIOUS))
        if (next != null) builder.addAction(jumpAction(R.drawable.skip_next, nextTitle, ACTION_NEXT))

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun jumpAction(
        icon: Int,
        title: String,
        action: String,
    ): Notification.Action {
        val intent =
            PendingIntent.getService(
                context,
                action.hashCode(),
                Intent(context, MusicService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Action
            .Builder(Icon.createWithResource(context, icon), title, intent)
            .apply {
                // Without this the lock screen asks for the PIN before running any action, which
                // is the whole thing this notification is here to avoid.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(false)
            }.build()
    }

    companion object {
        const val ACTION_PREVIOUS = "moe.rukamori.archivetune.action.LOCK_QUEUE_PREVIOUS"
        const val ACTION_NEXT = "moe.rukamori.archivetune.action.LOCK_QUEUE_NEXT"
        private const val CHANNEL_ID = "lock_queue_channel"
        private const val NOTIFICATION_ID = 889
    }
}
