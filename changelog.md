# changelog

## 1.2.1 — 2026-09-03

- Web edits now apply while the note is still open. Socket.IO was handing the operation list as a
  Java `List` (and sometimes as a JSON array in a different argument slot); those events were
  dropped, so the phone only caught up on a fresh `doc` after you left and came back.
- HedgeDoc 2 polling follows the editor as well as the reader.
- The editor shrinks above the keyboard, so a long note's last lines stay in view while you type.

## 1.2.0 — 2026-09-02

Live notes without a WebView, and no save button.

- **Native live view.** `NoteSession` holds the HedgeDoc 1.x Socket.IO document open while a note is
  on screen. The reader and the editor follow other people's typing as it happens, rendered by
  Markwon. The WebView live editor is still in the menu for the full HedgeDoc web UI, but it is no
  longer the only way to see a note update.
- **Checkboxes are tappable.** Tapping a task list checkbox in the reader toggles it and writes it,
  the same as HedgeDoc web in view mode.
- **The save button is gone.** Typing autosaves 1.2s after you stop. Leaving the editor flushes
  anything still waiting. A new note is created by its first autosave.
- **Fixed the stale view after a save.** HedgeDoc 1.x flushes the in-memory document to the database
  on a timer, so the app was re-downloading pre-save content and even caching it. Writes now settle
  the local copy from what was sent, and on a live session the sent operation already is the state.
- HedgeDoc 2 has no realtime protocol here, so the reader re-reads on a 6 second timer instead.
- `TextOperation` gained `compose`, `transform`, `fromJsonList` and a prefix/suffix `diff`, so an
  edit is now a small operation rather than a whole-document replace that could clobber a
  collaborator.
- 27 unit tests, `gradle :app:testDebugUnitTest` green, `lintDebug` clean.

## 1.1.0

- HedgeDoc 2 support (`/api/private` session and `/api/v2` bearer token), edition probe on connect
- History search, pinned filter, revisions, permissions, published links, PDF export (1.x)
- 12 Amni-Scient themes, offline cache, share text into the app
