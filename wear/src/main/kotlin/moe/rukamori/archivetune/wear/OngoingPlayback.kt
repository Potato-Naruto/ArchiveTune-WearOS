/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Keeps the track visible after the app is left: an ongoing notification registered as a Wear
 * Ongoing Activity, which is what the watch face chip, the recents row and Samsung's Now Bar read.
 * Tapping it returns to the player; its action toggles playback without opening anything.
 *
 * It shows what was playing when the app was left. Nothing on the watch hears from the phone
 * while the app is closed, so it times out on its own rather than tracking the phone.
 */
object OngoingPlayback {
    private const val CHANNEL_ID = "playback"
    private const val NOTIFICATION_ID = 1
    private const val TIMEOUT_MS = 3 * 60 * 60_000L

    fun show(
        context: Context,
        title: String,
        artist: String,
    ) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ongoing_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val togglePlay =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TogglePlayReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(artist)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openApp)
                .setTimeoutAfter(TIMEOUT_MS)
                .addAction(R.drawable.play, context.getString(R.string.play_pause), togglePlay)
        OngoingActivity
            .Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.logo)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(title).build())
            .build()
            .apply(context)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class TogglePlayReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WearMessagingClient(context).send(WearProtocol.PATH_TOGGLE_PLAY)
            } finally {
                pending.finish()
            }
        }
    }
}
