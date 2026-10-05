/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A GitHub release that carries a watch APK. */
data class WearRelease(
    val tag: String,
    val publishedAtMs: Long,
    val apkUrl: String,
    val apkBytes: Long,
)

/**
 * Self-updates the watch app from this repository's GitHub releases. The release's publish time is
 * compared with when this install was last updated, because the tags (`wearos-release-N`) carry no
 * version to compare.
 */
object WearUpdater {
    private const val API_LATEST = "https://api.github.com/repos/Potato-Naruto/ArchiveTune-WearOS/releases/latest"
    private const val APK_ASSET = "ArchiveTune-wear-release.apk"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 7301
    private const val JOB_ID = 7301
    const val PREFERENCES = "wear"

    sealed interface InstallState {
        data object Idle : InstallState

        data object Installing : InstallState

        data object Failed : InstallState
    }

    private val _installing = MutableStateFlow<InstallState>(InstallState.Idle)
    val installing: StateFlow<InstallState> = _installing.asStateFlow()

    /**
     * Updates are checked only when the user asks. Earlier builds could arm a periodic Wi-Fi job;
     * this removes one left over from them, and the preference that armed it.
     */
    fun cancelScheduledChecks(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove("auto_update").apply()
    }

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()

    /** Blocking; call off the main thread. Null when the latest release has no watch APK. */
    fun fetchLatest(): WearRelease? {
        val connection = open(API_LATEST)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") != APK_ASSET) continue
                return WearRelease(
                    tag = json.optString("tag_name"),
                    publishedAtMs = Instant.parse(json.optString("published_at")).toEpochMilli(),
                    apkUrl = asset.getString("browser_download_url"),
                    apkBytes = asset.optLong("size"),
                )
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(
        context: Context,
        release: WearRelease,
    ): Boolean {
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        return release.publishedAtMs > installedAt
    }

    /** Blocking; reports 0..100 when the size is known. */
    fun download(
        context: Context,
        release: WearRelease,
        onProgress: (Int) -> Unit = {},
    ): File {
        val target = File(context.cacheDir, "update.apk")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkBytes
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The watch asks before the first install; later ones can go through silently (Android 12+). */
    fun install(
        context: Context,
        apk: File,
    ) {
        _installing.value = InstallState.Installing
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val result =
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(result.intentSender)
            }
        } catch (error: Throwable) {
            _installing.value = InstallState.Failed
            throw error
        }
    }

    internal fun markIdle(failed: Boolean) {
        _installing.value = if (failed) InstallState.Failed else InstallState.Idle
    }

    internal fun notifyConfirm(
        context: Context,
        confirm: Intent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.updates), NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            NOTIFICATION_ID,
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.sync)
                .setContentTitle(context.getString(R.string.update_ready))
                .setContentText(context.getString(R.string.update_tap_to_install))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ArchiveTune-Wear")
        }
}

/** Receives the installer's verdict; anything but success clears the "installing" state. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    } ?: return
                // Opens straight away when the app is in front; from the background the system
                // blocks it, so the notification is the way in.
                runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                WearUpdater.notifyConfirm(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> WearUpdater.markIdle(failed = false)
            else -> WearUpdater.markIdle(failed = true)
        }
    }
}
