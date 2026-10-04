/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.playback.MusicService
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * The phone end of the Wear OS remote. Holds one [MediaBrowser] on [MusicService]'s session — the
 * same door Android Auto uses, so the watch browses the library tree and plays from it without
 * touching the database — and pushes playback state back to every watch that is listening.
 *
 * Everything here runs on the main thread, which is also the browser's application thread.
 */
internal object WearBridge {
    private const val TAG = "WearBridge"

    // A watch re-sends PATH_STATE every 15 s while its app is open; one that has gone quiet for
    // longer than this is dropped, and with nobody listening the browser is released so it stops
    // holding MusicService bound.
    private const val SUBSCRIBER_TTL_MS = 40_000L
    // The whole song list of a playlist in one message: about 150 bytes a row, so still well inside the
    // Data Layer's ~100 KB limit.
    private const val BROWSE_PAGE_SIZE = 400
    private const val SEARCH_PAGE_SIZE = 30
    private const val SEARCH_CONTAINER_LIMIT = 6

    // Big enough to fill a round watch face, small enough to stay far below the Data Layer's
    // ~100 KB message limit once JPEG-compressed.
    private const val ART_SIZE_PX = 320
    private const val ART_JPEG_QUALITY = 80

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val subscribers = HashMap<String, Long>()
    private var browser: MediaBrowser? = null
    private var idleJob: Job? = null
    private var publishJob: Job? = null
    private var artJob: Job? = null
    private var sentArtKey: String? = null

    /** Runs a request that only reads, without holding up the commands queued behind it. */
    fun launch(
        context: Context,
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) {
        scope.launch { handle(context, nodeId, path, payload) }
    }

    suspend fun handle(
        context: Context,
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) = withContext(Dispatchers.Main.immediate) {
        val appContext = context.applicationContext
        val text = payload.toString(Charsets.UTF_8).trim()
        try {
            val browser = connect(appContext)
            when (path) {
                WearProtocol.PATH_PLAY -> {
                    if (browser.playbackState == Player.STATE_IDLE) browser.prepare()
                    browser.play()
                }

                WearProtocol.PATH_PAUSE -> browser.pause()

                WearProtocol.PATH_SKIP_NEXT -> browser.seekToNext()

                WearProtocol.PATH_SKIP_PREV -> browser.seekToPrevious()

                WearProtocol.PATH_TOGGLE_SHUFFLE -> browser.shuffleModeEnabled = !browser.shuffleModeEnabled

                WearProtocol.PATH_VOLUME -> {
                    text.toIntOrNull()?.let { setVolume(appContext, it) }
                    publishState(appContext)
                }

                WearProtocol.PATH_SEEK -> {
                    text.toLongOrNull()?.let { delta ->
                        val duration = browser.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                        browser.seekTo((browser.currentPosition + delta).coerceIn(0L, duration))
                    }
                }

                WearProtocol.PATH_SEARCH_VOICE -> {
                    if (text.isNotEmpty()) browser.playAndAwait(searchItem(text))
                }

                WearProtocol.PATH_PLAY_ITEM -> {
                    val request = JSONObject(text)
                    playItem(
                        browser,
                        request.getString(WearProtocol.KEY_ID),
                        request.optBoolean(WearProtocol.KEY_SHUFFLE),
                    )
                }

                WearProtocol.PATH_STATE -> {
                    subscribers[nodeId] = SystemClock.elapsedRealtime()
                    send(appContext, nodeId, WearProtocol.PATH_STATE, stateJson(appContext, browser))
                    sendArt(appContext, browser, force = true)
                }

                WearProtocol.PATH_BROWSE -> {
                    val result = browser.getChildren(text, 0, BROWSE_PAGE_SIZE, null).await()
                    send(
                        appContext,
                        nodeId,
                        WearProtocol.PATH_BROWSE_RESULT,
                        listJson(text, result.value?.map(::entryJson)),
                    )
                }

                WearProtocol.PATH_SEARCH -> {
                    send(appContext, nodeId, WearProtocol.PATH_SEARCH_RESULT, listJson(text, search(browser, text)))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Wear request %s failed", path)
            when (path) {
                WearProtocol.PATH_BROWSE -> send(appContext, nodeId, WearProtocol.PATH_BROWSE_RESULT, listJson(text, null))
                WearProtocol.PATH_SEARCH -> send(appContext, nodeId, WearProtocol.PATH_SEARCH_RESULT, listJson(text, null))
            }
        } finally {
            scheduleIdleCheck()
        }
    }

    private suspend fun connect(context: Context): MediaBrowser {
        browser?.takeIf { it.isConnected }?.let { return it }
        val connected =
            MediaBrowser
                .Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .setListener(
                    object : MediaBrowser.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            if (browser === controller) browser = null
                        }
                    },
                ).buildAsync()
                .await()
        connected.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (subscribers.isEmpty()) return
                    // One track change raises several event batches; the watch needs one update.
                    publishJob?.cancel()
                    publishJob =
                        scope.launch {
                            delay(100)
                            publishState(context)
                            sendArt(context, connected, force = false)
                        }
                }
            },
        )
        browser = connected
        return connected
    }

    private fun scheduleIdleCheck() {
        idleJob?.cancel()
        idleJob =
            scope.launch {
                while (true) {
                    delay(SUBSCRIBER_TTL_MS)
                    val cutoff = SystemClock.elapsedRealtime() - SUBSCRIBER_TTL_MS
                    subscribers.values.removeAll { it < cutoff }
                    if (subscribers.isEmpty()) break
                }
                browser?.release()
                browser = null
                sentArtKey = null
            }
    }

    private suspend fun playItem(
        browser: MediaBrowser,
        id: String,
        shuffle: Boolean,
    ) {
        // Playlists have their own shuffle entry in the browse tree, which also picks a random
        // first song; every other container only gets the player's shuffle mode.
        val isPlaylist =
            (id.startsWith("${MusicService.PLAYLIST}/") || id.startsWith("${MusicService.ONLINE_PLAYLIST}/")) &&
                id.count { it == '/' } == 1
        if (shuffle && isPlaylist) {
            browser.playAndAwait(MediaItem.Builder().setMediaId("$id/_shuffle").build())
        } else {
            browser.shuffleModeEnabled = shuffle
            browser.playAndAwait(MediaItem.Builder().setMediaId(id).build())
        }
    }

    private fun searchItem(query: String): MediaItem =
        MediaItem
            .Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata
                    .Builder()
                    .setSearchQuery(query)
                    .build(),
            ).build()

    /**
     * Sets a placeholder the session resolves into a queue in `onSetMediaItems`, and suspends until
     * it has. The caller is the listener service, which keeps the process alive only while it
     * blocks — returning before the lookup finishes could lose the prepare/play queued behind it.
     */
    private suspend fun MediaBrowser.playAndAwait(placeholder: MediaItem) {
        setMediaItem(placeholder)
        prepare()
        play()
        suspendCancellableCoroutine { continuation ->
            val listener =
                object : Player.Listener {
                    override fun onEvents(
                        player: Player,
                        events: Player.Events,
                    ) {
                        val resolved = player.currentMediaItem != placeholder
                        if (!resolved && !player.isPlaying && player.playerError == null) return
                        removeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            addListener(listener)
            continuation.invokeOnCancellation { scope.launch { removeListener(listener) } }
        }
    }

    private fun setVolume(
        context: Context,
        volume: Int,
    ) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
    }

    private fun publishState(context: Context) {
        val browser = browser ?: return
        val state = stateJson(context, browser)
        subscribers.keys.forEach { send(context, it, WearProtocol.PATH_STATE, state) }
    }

    private fun stateJson(
        context: Context,
        browser: MediaBrowser,
    ): ByteArray {
        val metadata = browser.mediaMetadata
        val audioManager = context.getSystemService(AudioManager::class.java)
        return JSONObject()
            .put(WearProtocol.KEY_HAS_ITEM, browser.currentMediaItem != null)
            .put(WearProtocol.KEY_TITLE, metadata.title?.toString().orEmpty())
            .put(WearProtocol.KEY_ARTIST, metadata.artist?.toString().orEmpty())
            .put(WearProtocol.KEY_PLAYING, browser.isPlaying)
            .put(
                WearProtocol.KEY_PLAY_WHEN_READY,
                browser.playWhenReady && browser.playbackState != Player.STATE_ENDED,
            ).put(WearProtocol.KEY_SHUFFLE, browser.shuffleModeEnabled)
            .put(WearProtocol.KEY_POSITION_MS, browser.currentPosition)
            .put(WearProtocol.KEY_DURATION_MS, browser.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
            .put(WearProtocol.KEY_VOLUME, audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
            .put(WearProtocol.KEY_MAX_VOLUME, audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    /**
     * Songs come from the session's own search — the library first, then YouTube Music — which
     * also primes the cache `onSetMediaItems` plays a bare song id from. Albums and playlists are
     * looked up alongside and handed over as `online_playlist/` folders, which the same browse
     * tree already knows how to open, play and shuffle.
     *
     * @return null only when nothing could be searched at all.
     */
    private suspend fun search(
        browser: MediaBrowser,
        query: String,
    ): List<JSONObject>? =
        coroutineScope {
            val albums =
                async(Dispatchers.IO) {
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_ALBUM)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<AlbumItem>()
                        ?.take(SEARCH_CONTAINER_LIMIT)
                        ?.map { album ->
                            containerJson(
                                id = "${MusicService.ONLINE_PLAYLIST}/${album.playlistId}",
                                title = album.title,
                                subtitle = album.artists?.joinToString { it.name }.orEmpty(),
                                kind = WearProtocol.KIND_ALBUM,
                            )
                        }
                }
            val playlists =
                async(Dispatchers.IO) {
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<PlaylistItem>()
                        ?.take(SEARCH_CONTAINER_LIMIT)
                        ?.map { playlist ->
                            containerJson(
                                id = "${MusicService.ONLINE_PLAYLIST}/${playlist.id}",
                                title = playlist.title,
                                subtitle = playlist.author?.name.orEmpty(),
                                kind = WearProtocol.KIND_PLAYLIST,
                            )
                        }
                }
            val songs =
                try {
                    browser
                        .getSearchResult(query, 0, SEARCH_PAGE_SIZE, null)
                        .await()
                        .value
                        ?.map(::entryJson)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Session search failed")
                    null
                }
            val foundAlbums = albums.await()
            val foundPlaylists = playlists.await()
            if (songs == null && foundAlbums == null && foundPlaylists == null) {
                null
            } else {
                songs.orEmpty() + foundAlbums.orEmpty() + foundPlaylists.orEmpty()
            }
        }

    private fun entryJson(item: MediaItem): JSONObject {
        val metadata = item.mediaMetadata
        return JSONObject()
            .put(WearProtocol.KEY_ID, item.mediaId)
            .put(WearProtocol.KEY_TITLE, metadata.title?.toString().orEmpty())
            .put(WearProtocol.KEY_SUBTITLE, (metadata.subtitle ?: metadata.artist)?.toString().orEmpty())
            .put(WearProtocol.KEY_BROWSABLE, metadata.isBrowsable == true)
            .put(WearProtocol.KEY_PLAYABLE, metadata.isPlayable != false)
            .put(
                WearProtocol.KEY_KIND,
                when (metadata.mediaType) {
                    MediaMetadata.MEDIA_TYPE_ALBUM -> WearProtocol.KIND_ALBUM
                    MediaMetadata.MEDIA_TYPE_PLAYLIST -> WearProtocol.KIND_PLAYLIST
                    else -> WearProtocol.KIND_SONG
                },
            )
    }

    private fun containerJson(
        id: String,
        title: String,
        subtitle: String,
        kind: String,
    ): JSONObject =
        JSONObject()
            .put(WearProtocol.KEY_ID, id)
            .put(WearProtocol.KEY_TITLE, title)
            .put(WearProtocol.KEY_SUBTITLE, subtitle)
            .put(WearProtocol.KEY_BROWSABLE, true)
            .put(WearProtocol.KEY_PLAYABLE, true)
            .put(WearProtocol.KEY_KIND, kind)

    private fun listJson(
        requestId: String,
        entries: List<JSONObject>?,
    ): ByteArray {
        val array = JSONArray()
        entries
            .orEmpty()
            // "_shuffle", "_sort" and "_add_current_song" are Android Auto's action rows; the watch
            // has its own Play and Shuffle buttons.
            .filterNot { it.getString(WearProtocol.KEY_ID).substringAfterLast('/').startsWith("_") }
            .forEach { array.put(it) }
        return JSONObject()
            .put(WearProtocol.KEY_ID, requestId)
            .put(WearProtocol.KEY_ERROR, entries == null)
            .put(WearProtocol.KEY_ITEMS, array)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun sendArt(
        context: Context,
        browser: MediaBrowser,
        force: Boolean,
    ) {
        val metadata = browser.mediaMetadata
        val source: Any? = metadata.artworkUri ?: metadata.artworkData
        val key = metadata.artworkUri?.toString() ?: metadata.artworkData?.contentHashCode()?.toString()
        if (!force && key == sentArtKey) return
        sentArtKey = key
        artJob?.cancel()
        artJob =
            scope.launch {
                val bytes = source?.let { loadArt(context, it) } ?: ByteArray(0)
                subscribers.keys.forEach { send(context, it, WearProtocol.PATH_ART, bytes) }
            }
    }

    private suspend fun loadArt(
        context: Context,
        source: Any,
    ): ByteArray? =
        try {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(source)
                    .size(ART_SIZE_PX, ART_SIZE_PX)
                    .allowHardware(false)
                    .build()
            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
            withContext(Dispatchers.Default) {
                bitmap?.let {
                    ByteArrayOutputStream().use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, ART_JPEG_QUALITY, out)
                        out.toByteArray()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not load artwork for the watch")
            null
        }

    private fun send(
        context: Context,
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) {
        Wearable
            .getMessageClient(context)
            .sendMessage(nodeId, path, payload)
            .addOnFailureListener { Timber.tag(TAG).w(it, "Could not send %s to the watch", path) }
    }
}
