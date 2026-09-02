# HedgeDoc for Android

Unofficial native Kotlin client for [HedgeDoc 1.x](https://hedgedoc.org). HedgeDoc itself is AGPL, so this app is too. The HedgeDoc logo is under separate terms and is **not** used here.

Connect to a server you host, or `https://demo.hedgedoc.org`. Read history, write markdown, save, pin, share, export PDF, browse revisions, change permission, and open the live collaborative editor when you need it.

## Features

- Email, LDAP, guest, or browser `connect.sid` cookie (for OAuth-only servers)
- History with search, pinned filter, plant-label cards
- Markdown reader (tables, tasks, images)
- Native editor with preview and optional alias on create
- Save existing notes over HedgeDoc's Socket.IO OT path (full-document replace)
- Live editor (WebView with your session cookie)
- Pin, remove from history, delete note
- Share link, markdown, or PDF
- Published link, revisions, permission (`freely` … `private`)
- Offline cache of the last download
- Share text into the app as a new note

Saving an existing note replaces the whole document. If someone else is typing in it, your save can overwrite them. Use **Live editor** for real collaborative typing.

## Build

```
./gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Java 17 comes from Android Studio's JBR (`gradle.properties`).

## License

[AGPL-3.0-or-later](LICENSE). Fonts: Zilla Slab, Outfit, IBM Plex Mono under the SIL Open Font License.
