/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

/**
 * Message paths and JSON keys of the Wear OS remote. Mirrored by the file of the same name in the
 * :wear module — the two APKs share no code module, so a change here must be made there too.
 */
internal object WearProtocol {
    // Watch -> phone.
    const val PATH_PLAY = "/play"
    const val PATH_PAUSE = "/pause"
    const val PATH_SKIP_NEXT = "/skip_next"
    const val PATH_SKIP_PREV = "/skip_prev"
    const val PATH_TOGGLE_SHUFFLE = "/toggle_shuffle"
    const val PATH_VOLUME = "/volume"
    const val PATH_SEARCH_VOICE = "/search_voice"
    const val PATH_PLAY_ITEM = "/play_item"
    const val PATH_STATE = "/state"
    const val PATH_BROWSE = "/browse"
    const val PATH_SEARCH = "/search"

    // Phone -> watch. PATH_STATE is reused for the reply.
    const val PATH_ART = "/art"
    const val PATH_BROWSE_RESULT = "/browse_result"
    const val PATH_SEARCH_RESULT = "/search_result"

    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_HAS_ITEM = "hasItem"
    const val KEY_PLAYING = "playing"
    const val KEY_PLAY_WHEN_READY = "playWhenReady"
    const val KEY_SHUFFLE = "shuffle"
    const val KEY_POSITION_MS = "positionMs"
    const val KEY_DURATION_MS = "durationMs"
    const val KEY_VOLUME = "volume"
    const val KEY_MAX_VOLUME = "maxVolume"

    const val KEY_ID = "id"
    const val KEY_ITEMS = "items"
    const val KEY_ERROR = "error"
    const val KEY_SUBTITLE = "subtitle"
    const val KEY_BROWSABLE = "browsable"
    const val KEY_PLAYABLE = "playable"
}
