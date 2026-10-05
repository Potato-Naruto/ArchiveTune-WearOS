# Wear OS remote

`:wear` is a separate watch APK that controls playback on the phone. It plays nothing itself.

## How the two halves talk

Everything goes over the Wearable Data Layer's `MessageClient`. The Data Layer only routes between
apps with the **same `applicationId` and signing certificate**, so `wear/build.gradle.kts` mirrors
`:app` on both (including the `.debug` suffix and the release keystore).

The paths and JSON keys live in `WearProtocol.kt`, which exists twice — once in
`app/src/gms/kotlin/.../wear/`, once in `wear/src/main/kotlin/.../wear/` — because the two APKs share
no code module. Change both.

| Direction | Paths |
|---|---|
| watch → phone, commands | `/play` `/pause` `/toggle_play` `/skip_next` `/skip_prev` `/toggle_shuffle` `/repeat` `/volume` `/seek` `/search_voice` `/play_item` `/play_queue_item` `/queue_art` |
| watch → phone, reads | `/state` `/browse` `/search` `/sync` `/queue` |
| phone → watch | `/state` `/art` `/browse_result` `/search_result` `/sync_result` `/queue_result` `/queue_art_result` |

## Phone side (gms source set only)

`WearCommandListenerService` receives the messages and hands them to `WearBridge`, which holds one
`MediaBrowser` on `MusicService`'s session. The watch therefore sees the same browse tree Android
Auto does (`MediaLibrarySessionCallback.onGetChildren` / `onGetSearchResult`) and plays from it with
the same media ids (`onSetMediaItems`); nothing in the bridge reads the database.

- Commands block the listener thread until they are done. Play services keeps the service bound, and
  the process alive, only until the callback returns, and a search or playlist start needs the
  session connection to outlive its lookup.
- Reads run behind it, so a Pause is not stuck waiting for search results.
- State is pushed only to watches that keep asking: the watch app re-sends `/state` every 15 s while
  it is visible, a watch silent for 40 s is dropped, and with nobody listening the browser is
  released so it stops holding `MusicService` bound.
- Album art goes as a 320 px JPEG in its own `/art` message, far below the ~100 KB message limit.
- Volume is the phone's media stream volume.
- `/sync` enqueues `WearPlaylistSyncWorker`, which runs `SyncUtils` for liked songs, the saved
  playlists and then every playlist with a remote copy, and answers `/sync_result`. The watch caches
  what it has browsed for as long as the app is open; that reply is what clears the cache.
- `/search` returns songs from the session's own search (library first, then YouTube Music) plus
  albums and playlists looked up directly with `YouTube.search`. Those are handed over as
  `online_playlist/<id>` folders, which the browse tree already opens, plays and shuffles.

## Queue

The second page of the watch's home pager is the phone's queue: the songs that already played above
the current one, the ones coming below, scrolled so the playing song is centred. The phone sends a
window (15 before, 50 after) in *play order* — it walks the timeline with the shuffle mode, so "next"
is honest when shuffle is on — and pushes it again on a track change, a timeline change or a shuffle
toggle. Each row's id is its window index; `/play_queue_item` sends it back and the phone seeks there.

Rows show a 96 px thumbnail. They do not ride in the queue message (65 covers would blow the Data
Layer's ~100 KB limit): each row carries a stable `art` key (the media id), the watch asks with
`/queue_art` for the keys it has no thumbnail for, and the phone answers one `/queue_art_result`
per cover as it loads (`<key>
<jpeg bytes>`), four at a time.

## Watch side

`RemoteViewModel` owns all state; `ui/` holds the screens (player, queue, library, browse, search results,
volume, theme). Themes are in `WearTheme.kt`; AMOLED deliberately draws no album art.

On the player, holding Previous or Next for a second seeks 10 s at a time instead of skipping, and
the crown or a rotating bezel changes volume (`Modifier.volumeRotary`). A bezel reports itself
through the `android.hardware.rotaryencoder.lowres` feature and is treated as one volume step per
detent; a crown's continuous movement is accumulated into steps.

Settings (reached from the library) holds the theme, playlist sync and "sync when opened", the
cover-darkness slider that overrides each theme's own scrim, and a keep-screen-on switch
(`FLAG_KEEP_SCREEN_ON`, so it only applies while the app is in front).

Decorated themes draw their stickers, backdrops and chibi figures in `ui/ThemeDecor.kt` from
shapes; there are no image assets. The figures are original drawings.

When the app is left with a track loaded, `OngoingPlayback` posts an ongoing notification
registered as a Wear Ongoing Activity — the source for the watch-face chip, the launcher's recents
row and Samsung's Now Bar. It shows the track from the moment the app was left and times out after
three hours: nothing on the watch hears from the phone while the app is closed. Its action sends
`/toggle_play`.

`MainActivity` also accepts `android.media.action.MEDIA_PLAY_FROM_SEARCH` and forwards the query to
the phone, which is how "play … on ArchiveTune" from a voice assistant on the watch would arrive.

The watch prefers a node advertising the `archivetune_phone_playback` capability
(`app/src/gms/res/values/wear.xml`) and falls back to any connected node.

## Known gaps

- `wear.yml` (manual dispatch) builds `:wear` signed with the `KEYSTORE`/`KEY_ALIAS`/`KEYSTORE_PASSWORD`/`KEY_PASSWORD` secrets and can attach it to a release; nothing builds it on push.
- Emulators: a phone image and a watch image whose Play services are signed with different keys
  (a `dev-keys` preview phone against a `release-keys` watch) never exchange capabilities. Messages
  still route, which is what the connected-node fallback relies on.
- Play from a cold, backgrounded phone app on Android 12+ has not been exercised; every test so far
  had the app process already running.
- Nothing here has been tried with Gemini or on a Samsung watch. The assistant intent was sent by
  hand with `am start`, and the ongoing chip was seen on the emulator's stock watch face only. The
  chip's play/pause action has not been exercised.
- The bezel path has only been read, not run: the emulator has a crown, not a low-res encoder.
- Speech recognition itself is untested — on the emulator the query was typed through the speech
  screen's keyboard.
