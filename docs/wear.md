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
| watch → phone, commands | `/play` `/pause` `/skip_next` `/skip_prev` `/toggle_shuffle` `/volume` `/search_voice` `/play_item` |
| watch → phone, reads | `/state` `/browse` `/search` |
| phone → watch | `/state` `/art` `/browse_result` `/search_result` |

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

## Watch side

`RemoteViewModel` owns all state; `ui/` holds the screens (player, library, browse, search results,
volume, theme). Themes are in `WearTheme.kt`; AMOLED deliberately draws no album art.

The watch prefers a node advertising the `archivetune_phone_playback` capability
(`app/src/gms/res/values/wear.xml`) and falls back to any connected node.

## Known gaps

- CI neither builds nor publishes `:wear`.
- Emulators: a phone image and a watch image whose Play services are signed with different keys
  (a `dev-keys` preview phone against a `release-keys` watch) never exchange capabilities. Messages
  still route, which is what the connected-node fallback relies on.
- Play from a cold, backgrounded phone app on Android 12+ has not been exercised; every test so far
  had the app process already running.
- Speech recognition itself is untested — on the emulator the query was typed through the speech
  screen's keyboard.
