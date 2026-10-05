/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PlayerState(
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val playWhenReady: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = WearProtocol.REPEAT_OFF,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 0,
    val maxVolume: Int = 0,
    // The watch's own clock: the two devices' wall clocks are not guaranteed to agree.
    val receivedAtMs: Long = 0,
) {
    fun positionAt(nowMs: Long): Long = if (playing) positionMs + (nowMs - receivedAtMs) else positionMs
}

data class MediaEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val browsable: Boolean,
    val playable: Boolean,
    val kind: String = WearProtocol.KIND_SONG,
)

/** One row of the phone's queue. [id] is the phone's own index for it, handed back to play it. */
data class QueueEntry(
    val id: String,
    val title: String,
    val artist: String,
)

/** The songs around the current one in play order; [currentId] names the one playing. */
data class QueueState(
    val items: List<QueueEntry> = emptyList(),
    val currentId: String? = null,
) {
    val currentPosition: Int get() = items.indexOfFirst { it.id == currentId }
}

sealed interface SyncState {
    data object Idle : SyncState

    data object Syncing : SyncState

    data object Failed : SyncState

    data class Done(
        val playlists: Int,
    ) : SyncState
}

sealed interface ListState {
    data object Loading : ListState

    data object Failed : ListState

    data class Loaded(
        val items: List<MediaEntry>,
    ) : ListState
}

class RemoteViewModel(
    application: Application,
) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {
    private val client = WearMessagingClient(application)
    private val preferences = application.getSharedPreferences("wear", Context.MODE_PRIVATE)

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val _art = MutableStateFlow<ImageBitmap?>(null)
    val art: StateFlow<ImageBitmap?> = _art.asStateFlow()

    private val _phoneReachable = MutableStateFlow(true)
    val phoneReachable: StateFlow<Boolean> = _phoneReachable.asStateFlow()

    private val _browse = MutableStateFlow<Map<String, ListState>>(emptyMap())
    val browse: StateFlow<Map<String, ListState>> = _browse.asStateFlow()

    private val _search = MutableStateFlow<Map<String, ListState>>(emptyMap())
    val search: StateFlow<Map<String, ListState>> = _search.asStateFlow()

    private val _theme = MutableStateFlow(WearTheme.fromKey(preferences.getString(KEY_THEME, null)))
    val theme: StateFlow<WearTheme> = _theme.asStateFlow()

    // Null until the user moves the slider: until then each theme uses its own darkness.
    private val _artDim = MutableStateFlow(preferences.getFloat(KEY_ART_DIM, -1f).takeIf { it >= 0f })
    val artDim: StateFlow<Float?> = _artDim.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(preferences.getBoolean(KEY_KEEP_SCREEN_ON, false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _syncOnOpen = MutableStateFlow(preferences.getBoolean(KEY_SYNC_ON_OPEN, false))
    val syncOnOpen: StateFlow<Boolean> = _syncOnOpen.asStateFlow()

    private var syncedThisLaunch = false

    // Bumped whenever something starts playing from a list, so the home pager returns to the player.
    private val _showPlayer = MutableStateFlow(0)
    val showPlayer: StateFlow<Int> = _showPlayer.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private var heartbeat: Job? = null
    private var syncTimeout: Job? = null
    private var volumeChangedAtMs = 0L

    /** Called while the app is visible: the phone only pushes state to watches that keep asking. */
    fun start() {
        Wearable.getMessageClient(getApplication<Application>()).addListener(this)
        OngoingPlayback.hide(getApplication())
        if (_syncOnOpen.value && !syncedThisLaunch) {
            syncedThisLaunch = true
            syncPlaylists()
        }
        heartbeat?.cancel()
        heartbeat =
            viewModelScope.launch {
                while (true) {
                    _phoneReachable.value = client.send(WearProtocol.PATH_STATE)
                    delay(HEARTBEAT_MS)
                }
            }
    }

    fun stop() {
        val state = _player.value
        if (state.hasItem) OngoingPlayback.show(getApplication(), state.title, state.artist)
        heartbeat?.cancel()
        Wearable.getMessageClient(getApplication<Application>()).removeListener(this)
    }

    fun togglePlay() {
        val wasPlaying = _player.value.playWhenReady
        _player.update { it.copy(playWhenReady = !wasPlaying, playing = false) }
        send(if (wasPlaying) WearProtocol.PATH_PAUSE else WearProtocol.PATH_PLAY)
    }

    fun skipNext() = send(WearProtocol.PATH_SKIP_NEXT)

    /** The phone also pushes the queue whenever it changes; this is for when the screen opens. */
    fun loadQueue() = send(WearProtocol.PATH_QUEUE)

    fun playQueueItem(id: String) {
        _queue.update { it.copy(currentId = id) }
        send(WearProtocol.PATH_PLAY_QUEUE_ITEM, id)
    }

    fun skipPrevious() = send(WearProtocol.PATH_SKIP_PREV)

    fun toggleShuffle() {
        _player.update { it.copy(shuffle = !it.shuffle) }
        send(WearProtocol.PATH_TOGGLE_SHUFFLE)
    }

    /** Off, then the whole queue, then the current song, then off again. */
    fun cycleRepeat() {
        val next =
            when (_player.value.repeatMode) {
                WearProtocol.REPEAT_OFF -> WearProtocol.REPEAT_ALL
                WearProtocol.REPEAT_ALL -> WearProtocol.REPEAT_ONE
                else -> WearProtocol.REPEAT_OFF
            }
        _player.update { it.copy(repeatMode = next) }
        send(WearProtocol.PATH_REPEAT, next.toString())
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, _player.value.maxVolume)
        if (clamped == _player.value.volume) return
        volumeChangedAtMs = SystemClock.elapsedRealtime()
        _player.update { it.copy(volume = clamped) }
        send(WearProtocol.PATH_VOLUME, clamped.toString())
    }

    fun seekBy(deltaMs: Long) {
        val now = SystemClock.elapsedRealtime()
        _player.update {
            val target = (it.positionAt(now) + deltaMs).coerceIn(0L, it.durationMs.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)
            it.copy(positionMs = target, receivedAtMs = now)
        }
        send(WearProtocol.PATH_SEEK, deltaMs.toString())
    }

    /** Replaces the phone's queue with the best matches for [query] and plays it. */
    fun playSearch(query: String) {
        send(WearProtocol.PATH_SEARCH_VOICE, query)
        _showPlayer.update { it + 1 }
    }

    fun playItem(
        id: String,
        shuffle: Boolean = false,
    ) {
        val request =
            JSONObject()
                .put(WearProtocol.KEY_ID, id)
                .put(WearProtocol.KEY_SHUFFLE, shuffle)
                .toString()
        send(WearProtocol.PATH_PLAY_ITEM, request)
        _showPlayer.update { it + 1 }
    }

    fun loadChildren(
        parentId: String,
        force: Boolean = false,
    ) {
        if (!force && _browse.value[parentId] is ListState.Loaded) return
        _browse.update { it + (parentId to ListState.Loading) }
        request(WearProtocol.PATH_BROWSE, parentId) { _browse.update { it + (parentId to ListState.Failed) } }
    }

    fun loadSearch(
        query: String,
        force: Boolean = false,
    ) {
        if (!force && _search.value[query] is ListState.Loaded) return
        _search.update { it + (query to ListState.Loading) }
        request(WearProtocol.PATH_SEARCH, query) { _search.update { it + (query to ListState.Failed) } }
    }

    /**
     * Asks the phone to refresh its playlists from YouTube Music. Everything browsed so far is
     * dropped when it reports back, so counts and song lists are read again rather than served from
     * what the watch remembered.
     */
    fun syncPlaylists() {
        if (_sync.value == SyncState.Syncing) return
        _sync.value = SyncState.Syncing
        request(WearProtocol.PATH_SYNC, "") { _sync.value = SyncState.Failed }
        syncTimeout?.cancel()
        syncTimeout =
            viewModelScope.launch {
                delay(SYNC_TIMEOUT_MS)
                if (_sync.value == SyncState.Syncing) _sync.value = SyncState.Failed
            }
    }

    fun setTheme(theme: WearTheme) {
        _theme.value = theme
        preferences.edit().putString(KEY_THEME, theme.key).apply()
    }

    fun setArtDim(dim: Float?) {
        _artDim.value = dim
        preferences.edit().putFloat(KEY_ART_DIM, dim ?: -1f).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun setSyncOnOpen(enabled: Boolean) {
        _syncOnOpen.value = enabled
        preferences.edit().putBoolean(KEY_SYNC_ON_OPEN, enabled).apply()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.PATH_STATE -> {
                val json = JSONObject(event.data.toString(Charsets.UTF_8))
                val now = SystemClock.elapsedRealtime()
                // While the crown or bezel is turning, replies to earlier steps arrive carrying
                // volumes the watch has already moved past; taking them would make the bar jump back.
                val keepLocalVolume = now - volumeChangedAtMs < VOLUME_SETTLE_MS
                _player.value =
                    PlayerState(
                        hasItem = json.optBoolean(WearProtocol.KEY_HAS_ITEM),
                        title = json.optString(WearProtocol.KEY_TITLE),
                        artist = json.optString(WearProtocol.KEY_ARTIST),
                        playing = json.optBoolean(WearProtocol.KEY_PLAYING),
                        playWhenReady = json.optBoolean(WearProtocol.KEY_PLAY_WHEN_READY),
                        shuffle = json.optBoolean(WearProtocol.KEY_SHUFFLE),
                        repeatMode = json.optInt(WearProtocol.KEY_REPEAT),
                        positionMs = json.optLong(WearProtocol.KEY_POSITION_MS),
                        durationMs = json.optLong(WearProtocol.KEY_DURATION_MS),
                        volume =
                            if (keepLocalVolume) _player.value.volume else json.optInt(WearProtocol.KEY_VOLUME),
                        maxVolume = json.optInt(WearProtocol.KEY_MAX_VOLUME),
                        receivedAtMs = now,
                    )
            }

            WearProtocol.PATH_QUEUE_RESULT -> {
                val json = JSONObject(event.data.toString(Charsets.UTF_8))
                val array = json.getJSONArray(WearProtocol.KEY_ITEMS)
                val current = json.optInt(WearProtocol.KEY_CURRENT, -1)
                _queue.value =
                    QueueState(
                        items =
                            List(array.length()) { index ->
                                val item = array.getJSONObject(index)
                                QueueEntry(
                                    id = item.optString(WearProtocol.KEY_ID),
                                    title = item.optString(WearProtocol.KEY_TITLE),
                                    artist = item.optString(WearProtocol.KEY_SUBTITLE),
                                )
                            },
                        currentId = current.takeIf { it >= 0 }?.toString(),
                    )
            }

            WearProtocol.PATH_ART -> {
                val bytes = event.data
                viewModelScope.launch {
                    _art.value =
                        withContext(Dispatchers.Default) {
                            if (bytes.isEmpty()) {
                                null
                            } else {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                }
            }

            WearProtocol.PATH_SYNC_RESULT -> {
                val json = JSONObject(event.data.toString(Charsets.UTF_8))
                syncTimeout?.cancel()
                _sync.value =
                    if (json.optBoolean(WearProtocol.KEY_ERROR)) {
                        SyncState.Failed
                    } else {
                        SyncState.Done(json.optInt(WearProtocol.KEY_COUNT))
                    }
                _browse.value = emptyMap()
                loadChildren(PLAYLISTS_ID, force = true)
            }

            WearProtocol.PATH_BROWSE_RESULT -> {
                val (id, state) = parseList(event.data)
                _browse.update { it + (id to state) }
            }

            WearProtocol.PATH_SEARCH_RESULT -> {
                val (id, state) = parseList(event.data)
                _search.update { it + (id to state) }
            }
        }
    }

    private fun parseList(data: ByteArray): Pair<String, ListState> {
        val json = JSONObject(data.toString(Charsets.UTF_8))
        val id = json.optString(WearProtocol.KEY_ID)
        if (json.optBoolean(WearProtocol.KEY_ERROR)) return id to ListState.Failed
        val array = json.getJSONArray(WearProtocol.KEY_ITEMS)
        val items =
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MediaEntry(
                    id = item.optString(WearProtocol.KEY_ID),
                    title = item.optString(WearProtocol.KEY_TITLE),
                    subtitle = item.optString(WearProtocol.KEY_SUBTITLE),
                    browsable = item.optBoolean(WearProtocol.KEY_BROWSABLE),
                    playable = item.optBoolean(WearProtocol.KEY_PLAYABLE),
                    kind = item.optString(WearProtocol.KEY_KIND, WearProtocol.KIND_SONG),
                )
            }
        return id to ListState.Loaded(items)
    }

    private fun send(
        path: String,
        text: String? = null,
    ) {
        viewModelScope.launch {
            _phoneReachable.value = if (text == null) client.send(path) else client.send(path, text)
        }
    }

    private fun request(
        path: String,
        text: String,
        onUnsent: () -> Unit,
    ) {
        viewModelScope.launch {
            val sent = client.send(path, text)
            _phoneReachable.value = sent
            if (!sent) onUnsent()
        }
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        /** The playlists folder of the phone's browse tree (`MusicService.PLAYLIST`). */
        const val PLAYLISTS_ID = "playlist"

        private const val SYNC_TIMEOUT_MS = 5 * 60_000L
        private const val KEY_THEME = "theme"
        private const val KEY_ART_DIM = "artDim"
        private const val KEY_KEEP_SCREEN_ON = "keepScreenOn"
        private const val KEY_SYNC_ON_OPEN = "syncOnOpen"
        private const val HEARTBEAT_MS = 15_000L
        private const val VOLUME_SETTLE_MS = 1_500L
    }
}
