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
)

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

    // Bumped whenever something starts playing from a list, so the home pager returns to the player.
    private val _showPlayer = MutableStateFlow(0)
    val showPlayer: StateFlow<Int> = _showPlayer.asStateFlow()

    private var heartbeat: Job? = null

    /** Called while the app is visible: the phone only pushes state to watches that keep asking. */
    fun start() {
        Wearable.getMessageClient(getApplication<Application>()).addListener(this)
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
        heartbeat?.cancel()
        Wearable.getMessageClient(getApplication<Application>()).removeListener(this)
    }

    fun togglePlay() {
        val wasPlaying = _player.value.playWhenReady
        _player.update { it.copy(playWhenReady = !wasPlaying, playing = false) }
        send(if (wasPlaying) WearProtocol.PATH_PAUSE else WearProtocol.PATH_PLAY)
    }

    fun skipNext() = send(WearProtocol.PATH_SKIP_NEXT)

    fun skipPrevious() = send(WearProtocol.PATH_SKIP_PREV)

    fun toggleShuffle() {
        _player.update { it.copy(shuffle = !it.shuffle) }
        send(WearProtocol.PATH_TOGGLE_SHUFFLE)
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, _player.value.maxVolume)
        if (clamped == _player.value.volume) return
        _player.update { it.copy(volume = clamped) }
        send(WearProtocol.PATH_VOLUME, clamped.toString())
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

    fun setTheme(theme: WearTheme) {
        _theme.value = theme
        preferences.edit().putString(KEY_THEME, theme.key).apply()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.PATH_STATE -> {
                val json = JSONObject(event.data.toString(Charsets.UTF_8))
                _player.value =
                    PlayerState(
                        hasItem = json.optBoolean(WearProtocol.KEY_HAS_ITEM),
                        title = json.optString(WearProtocol.KEY_TITLE),
                        artist = json.optString(WearProtocol.KEY_ARTIST),
                        playing = json.optBoolean(WearProtocol.KEY_PLAYING),
                        playWhenReady = json.optBoolean(WearProtocol.KEY_PLAY_WHEN_READY),
                        shuffle = json.optBoolean(WearProtocol.KEY_SHUFFLE),
                        positionMs = json.optLong(WearProtocol.KEY_POSITION_MS),
                        durationMs = json.optLong(WearProtocol.KEY_DURATION_MS),
                        volume = json.optInt(WearProtocol.KEY_VOLUME),
                        maxVolume = json.optInt(WearProtocol.KEY_MAX_VOLUME),
                        receivedAtMs = SystemClock.elapsedRealtime(),
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

    private companion object {
        const val KEY_THEME = "theme"
        const val HEARTBEAT_MS = 15_000L
    }
}
