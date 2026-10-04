/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.utils.SyncUtils
import org.json.JSONObject
import timber.log.Timber

/**
 * The watch's "Sync playlists": refreshes liked songs and the saved-playlist list from YouTube
 * Music, then re-reads the songs of every playlist that has a remote copy, and tells the watch when
 * it is done so it can reload.
 *
 * A worker rather than a coroutine in [WearBridge] because this can run for a while with the phone
 * app in the background, where a process holding nothing but a coroutine is frozen within seconds.
 */
class WearPlaylistSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val nodeId = inputData.getString(KEY_NODE_ID) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WearSyncEntryPoint::class.java)
        val syncUtils = entryPoint.syncUtils()
        var synced = 0
        var failed = 0
        try {
            syncUtils.syncLikedSongs()
            syncUtils.syncSavedPlaylists()
            entryPoint
                .database()
                .playlistsByNameAsc()
                .first()
                .forEach { playlist ->
                    val browseId = playlist.playlist.browseId ?: return@forEach
                    try {
                        syncUtils.syncPlaylistNow(browseId, playlist.playlist.id, propagateFailures = true)
                        synced++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Could not sync playlist %s", playlist.playlist.name)
                        failed++
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Playlist sync for the watch failed")
            failed++
        }

        val reply =
            JSONObject()
                .put(WearProtocol.KEY_ERROR, failed > 0 && synced == 0)
                .put(WearProtocol.KEY_COUNT, synced)
                .toString()
                .toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            try {
                Tasks.await(
                    Wearable
                        .getMessageClient(applicationContext)
                        .sendMessage(nodeId, WearProtocol.PATH_SYNC_RESULT, reply),
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not report the sync result to the watch")
            }
        }
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WearSyncEntryPoint {
        fun syncUtils(): SyncUtils

        fun database(): MusicDatabase
    }

    companion object {
        private const val TAG = "WearSync"
        private const val WORK_NAME = "wear_playlist_sync"
        private const val KEY_NODE_ID = "nodeId"

        fun enqueue(
            context: Context,
            nodeId: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<WearPlaylistSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(KEY_NODE_ID to nodeId))
                    .build()
            // KEEP: a second tap while a sync is running should not start another one.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
