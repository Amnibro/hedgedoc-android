# HedgeDoc for Android

Unofficial native Kotlin client for [HedgeDoc](https://hedgedoc.org) 1.x and 2. HedgeDoc itself is AGPL, so this app is too. The HedgeDoc logo is under separate terms and is **not** used here.

<p>
<img src="docs/screenshots/connect.png" width="280" alt="Connect screen with Scient theme and auth methods" />
<img src="docs/screenshots/editor.png" width="280" alt="New note editor" />
</p>

Connect to a server you host, or `https://demo.hedgedoc.org`. The app asks `/api/private/config` whether the instance is HedgeDoc 2. 1.x keeps the old REST + Socket.IO path. 2 uses `/api/private` (session + CSRF) and `/api/v2` (bearer token).

## Install

[Download the latest APK](https://github.com/Amnibro/hedgedoc-android/releases/latest). Two files:

- **signed** — installable sideload. Signed with the Android debug certificate, so Play Protect may warn. Package `org.hedgedoc.android`.
- **unsigned** — same release build, no signature. Sign it with your own key if you want.

```
adb install -r hedgedoc-android-1.1.0-signed.apk
```

Min Android 8.0 (API 26).

## Features

- Email/username, LDAP, guest, browser session cookie, or HedgeDoc 2 API token
- History with search, pinned filter, plant-label cards
- Markdown reader (tables, tasks, images)
- Native editor with preview and optional alias on create
- HedgeDoc 1.x: save existing notes over Socket.IO OT (full-document replace)
- HedgeDoc 2: create/update/delete over REST
- Live editor (WebView with your session cookie; `/n/…` on HedgeDoc 2)
- Pin, remove from history, delete note
- Share link, markdown, or PDF (PDF is 1.x)
- Published link, revisions, permission (1.x: freely…private; 2: public/private)
- Offline cache of the last download
- Share text into the app as a new note
- Amni-Scient themes

Saving an existing 1.x note replaces the whole document. If someone else is typing in it, your save can overwrite them. Use **Live editor** for real collaborative typing.

## Build

```
./gradlew.bat assembleDebug assembleRelease
```

- Debug (already signed, `.debug` package): `app/build/outputs/apk/debug/app-debug.apk`
- Release unsigned: `app/build/outputs/apk/release/app-release-unsigned.apk`

Java 17 comes from Android Studio's JBR (`gradle.properties`).

## License

[AGPL-3.0-or-later](LICENSE). Fonts: Zilla Slab, Outfit, IBM Plex Mono under the SIL Open Font License.
