# architecture_map — hedgedoc-android

Unofficial native Kotlin/Compose client for HedgeDoc 1.x and 2. No WebView on the reading or
editing path. Package `org.hedgedoc.android`, min API 26, single activity.

## Layers

```
MainActivity            single Compose host, routes on Dest, handles share intents
  ui/AppViewModel       all state (UiState), live session lifecycle, autosave debounce
    ui/screens/*        Connect, Notes, Reader, Editor, LiveEditor (WebView), Settings
    ui/components/*     MarkdownPane (Markwon), ThemePicker, Widgets
    ui/theme/*          12 Amni-Scient palettes
  data/HedgeRepository  the only thing screens' state talks to; picks 1.x vs 2 per call
    data/HedgeApi       HedgeDoc 1.x REST
    data/HedgeV2        HedgeDoc 2 /api/private + /api/v2
    data/NoteSession    HedgeDoc 1.x realtime document (Socket.IO OT), held open
    data/OtClient       HedgeDoc 1.x one-shot socket ops (save, delete, permission)
    data/TextOperation  ShareJS/ot.js operations: apply, compose, transform, diff
    data/MarkdownTasks  task list checkboxes in markdown source
    data/SessionStore   DataStore session + cookies + theme
    data/NoteCache      last downloaded markdown per note
```

## Editions

`/api/private/config` decides. `HedgeEdition.V1` uses the old REST plus Socket.IO. `HedgeEdition.V2`
uses session/CSRF or a bearer token over REST only.

## Live documents (1.x)

`NoteSession` holds one Socket.IO connection per open note for as long as that note is on screen.

- `doc` seeds the text and the revision.
- `operation` from another client is applied locally, transformed first against anything of ours
  that is still unacknowledged.
- Local edits are diffed against the session's own text, so one keystroke is one small operation
  instead of a whole-document replace.
- One operation is in flight at a time. Edits made while waiting compose into a buffer that is sent
  on `ack`. This is the ot.js client state machine (Synchronized / AwaitingConfirm / AwaitingWithBuffer).

`NoteSession.text` is a `StateFlow`, so the reader and the editor both render the server document
without polling and without a WebView.

## Why saves never re-download

HedgeDoc 1.x keeps the live document in memory and writes it to the database on a timer.
`GET /:noteId/download` right after a save returns the version from *before* the save. So a write
settles the local copy from what we sent (`HedgeRepository.openNote(known = ...)`), never from a
fresh read. On a live session there is nothing to settle at all: our own operation already moved
`NoteSession.text`.

## Editions without realtime

HedgeDoc 2 has no realtime protocol this app speaks. `liveSession()` returns null and the reader
re-reads on a 6 second timer while it is the visible screen and nothing is mid-save.

## Writing

There is no save button. `AppViewModel.edit` debounces 1.2s, then `commit` writes through the live
session when there is one and falls back to `HedgeRepository.saveNote` otherwise. Leaving the editor
flushes whatever the debounce was still holding. A brand new note is created by the first autosave
once there is any text.

## Checkboxes

`MarkdownPane` hit-tests taps against Markwon's `TaskListSpan` leading margin and reports the
checkbox ordinal. `MarkdownTasks.toggle` flips the matching line in the markdown source, counting
task items the same way Markwon renders them (document order, fenced code skipped). The result goes
out through the same write path as any other edit.

## Ports and hosts

None. The app talks only to the HedgeDoc server the user connects to.

## Build

`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` then
`gradle -p . :app:assembleDebug :app:assembleRelease`. The PATH `java` on this machine is Java 8 and
will not configure AGP 8.5.
