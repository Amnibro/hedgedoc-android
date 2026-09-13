package org.hedgedoc.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.hedgedoc.android.data.AuthMethod
import org.hedgedoc.android.data.HedgeRepository
import org.hedgedoc.android.data.HedgeUrls
import org.hedgedoc.android.data.HistoryNote
import org.hedgedoc.android.data.LiveStatus
import org.hedgedoc.android.data.MarkdownTasks
import org.hedgedoc.android.data.NoteFilter
import org.hedgedoc.android.data.NoteSession
import org.hedgedoc.android.data.OpenNote
import org.hedgedoc.android.data.OutgoingShare
import org.hedgedoc.android.data.Revision
import org.hedgedoc.android.data.Session

sealed class Dest {
    data object Connect : Dest()
    data object Notes : Dest()
    data class Read(val id: String) : Dest()
    data class Edit(val id: String?, val seed: String) : Dest()
    data class Live(val id: String) : Dest()
    data object Settings : Dest()
}

enum class SyncState { IDLE, PENDING, SAVING, SAVED, FAILED }

data class UiState(
    val ready: Boolean = false,
    val dest: Dest = Dest.Connect,
    val session: Session? = null,
    val notes: List<HistoryNote> = emptyList(),
    val query: String = "",
    val filter: NoteFilter = NoteFilter.ALL,
    val loading: Boolean = false,
    val sync: SyncState = SyncState.IDLE,
    val liveStatus: LiveStatus? = null,
    val createdId: String? = null,
    val error: String? = null,
    val note: OpenNote? = null,
    val share: OutgoingShare? = null,
    val snack: String? = null,
    val revisions: List<Revision> = emptyList(),
    val permission: String = "",
    val serverStatus: String = "",
    val themeId: String = "scient",
) {
    val filtered: List<HistoryNote>
        get() {
            val base = if (filter == NoteFilter.PINNED) notes.filter { it.pinned } else notes
            val q = query.trim().lowercase()
            if (q.isEmpty()) return base
            return base.filter { note ->
                note.title.lowercase().contains(q) ||
                    note.tags.any { it.lowercase().contains(q) } ||
                    note.id.lowercase().contains(q)
            }
        }
}

class AppViewModel(private val repo: HedgeRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val http get() = repo.http

    private var live: NoteSession? = null
    private var liveJob: Job? = null
    private var autosaveJob: Job? = null
    private var pendingEdit: PendingEdit? = null

    private data class PendingEdit(val id: String?, val markdown: String, val alias: String?)

    fun cookieHeader(): String {
        val server = _state.value.session?.serverUrl ?: return ""
        return repo.cookieHeader(server)
    }

    init {
        viewModelScope.launch {
            repo.restoreCookies()
            launch {
                repo.themeId.collect { id ->
                    _state.update { it.copy(themeId = id) }
                }
            }
            repo.session.collect { session ->
                val dest = if (session == null) Dest.Connect else {
                    when (_state.value.dest) {
                        Dest.Connect -> Dest.Notes
                        else -> _state.value.dest
                    }
                }
                _state.update { it.copy(session = session, dest = dest, ready = true) }
                if (session != null && _state.value.notes.isEmpty()) {
                    refresh()
                }
            }
        }
    }

    override fun onCleared() {
        stopLive()
        super.onCleared()
    }

    fun consumeShare() { _state.update { it.copy(share = null) } }
    fun consumeSnack() { _state.update { it.copy(snack = null) } }

    fun go(dest: Dest) {
        if (dest is Dest.Notes || dest is Dest.Settings || dest is Dest.Connect) stopLive()
        _state.update { it.copy(dest = dest, error = null) }
    }

    fun back() {
        when (val current = _state.value.dest) {
            is Dest.Live -> _state.update { it.copy(dest = Dest.Read(current.id), error = null) }
            is Dest.Edit -> leaveEditor(current)
            is Dest.Read, Dest.Settings -> {
                stopLive()
                _state.update { it.copy(dest = Dest.Notes, error = null, sync = SyncState.IDLE) }
            }
            else -> Unit
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        val session = _state.value.session ?: return
        val id = HedgeUrls.noteIdFromUrl(value, session.serverUrl.toHttpUrlOrNull())
        if (!id.isNullOrBlank() && value.contains("://")) {
            open(id)
        }
    }

    fun setFilter(filter: NoteFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun connect(server: String, method: AuthMethod, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                repo.connect(server, method, username, password)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not connect.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val notes = repo.history()
                _state.update { it.copy(notes = notes) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not load history.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun open(noteId: String) {
        stopLive()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    dest = Dest.Read(noteId),
                    revisions = emptyList(),
                    sync = SyncState.IDLE,
                    createdId = null,
                )
            }
            try {
                val note = repo.openNote(noteId)
                _state.update { it.copy(note = note) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not open note.") }
            } finally {
                _state.update { it.copy(loading = false) }
                startLive(noteId)
            }
        }
    }

    fun startNew(seed: String = "") {
        stopLive()
        _state.update {
            it.copy(dest = Dest.Edit(null, seed), note = null, error = null, createdId = null, sync = SyncState.IDLE)
        }
    }

    fun startEdit() {
        val note = _state.value.note ?: return
        _state.update { it.copy(dest = Dest.Edit(note.id, note.markdown), error = null, sync = SyncState.IDLE) }
    }

    fun openLive(noteId: String = _state.value.note?.id.orEmpty()) {
        if (noteId.isBlank()) return
        _state.update { it.copy(dest = Dest.Live(noteId), error = null) }
    }

    /**
     * Every keystroke lands here. The write itself waits [AUTOSAVE_DELAY] so a burst of typing is
     * one operation, the same way the HedgeDoc web editor behaves.
     */
    fun edit(id: String?, markdown: String, alias: String?) {
        pendingEdit = PendingEdit(id, markdown, alias)
        _state.update { it.copy(sync = SyncState.PENDING) }
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY)
            commit(id, markdown, alias)
            pendingEdit = null
        }
    }

    fun toggleTask(index: Int) {
        val note = _state.value.note ?: return
        val updated = MarkdownTasks.toggle(note.markdown, index) ?: return
        _state.update { it.copy(note = note.copy(markdown = updated), sync = SyncState.SAVING, error = null) }
        viewModelScope.launch {
            try {
                push(note.id, updated)
                repo.cacheNote(note.id, updated)
                _state.update { it.copy(sync = SyncState.SAVED) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        note = note,
                        sync = SyncState.FAILED,
                        error = e.message ?: "Could not save that checkbox.",
                    )
                }
            }
        }
    }

    fun pin(note: HistoryNote) {
        viewModelScope.launch {
            try {
                repo.setPinned(note.id, !note.pinned)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun forget(noteId: String) {
        viewModelScope.launch {
            try {
                repo.removeFromHistory(noteId)
                refresh()
                if ((_state.value.dest as? Dest.Read)?.id == noteId || (_state.value.dest as? Dest.Live)?.id == noteId) {
                    stopLive()
                    _state.update { it.copy(dest = Dest.Notes, note = null, snack = "Removed from history") }
                } else {
                    _state.update { it.copy(snack = "Removed from history") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun destroy(noteId: String) {
        viewModelScope.launch {
            try {
                stopLive()
                repo.deleteNote(noteId)
                runCatching { repo.removeFromHistory(noteId) }
                refresh()
                _state.update { it.copy(dest = Dest.Notes, note = null, snack = "Note deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Delete failed. You probably need to own the note.") }
            }
        }
    }

    fun shareLink(noteId: String? = null) {
        viewModelScope.launch {
            val id = noteId ?: (_state.value.dest as? Dest.Read)?.id ?: _state.value.note?.id ?: return@launch
            try {
                val link = repo.noteLink(id)
                _state.update { it.copy(share = OutgoingShare(text = link, mime = "text/plain", chooserTitle = "Share note")) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun shareMarkdown() {
        val note = _state.value.note ?: return
        _state.update {
            it.copy(
                share = OutgoingShare(
                    text = note.markdown,
                    mime = "text/markdown",
                    chooserTitle = "Share markdown",
                ),
            )
        }
    }

    fun sharePdf() {
        viewModelScope.launch {
            val id = _state.value.note?.id ?: return@launch
            try {
                val bytes = repo.pdf(id)
                _state.update {
                    it.copy(
                        share = OutgoingShare(
                            bytes = bytes,
                            fileName = "$id.pdf",
                            mime = "application/pdf",
                            chooserTitle = "Share PDF",
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun loadRevisions() {
        viewModelScope.launch {
            val id = _state.value.note?.id ?: return@launch
            try {
                _state.update { it.copy(revisions = repo.revisions(id), error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun openRevision(revision: Revision) {
        viewModelScope.launch {
            val id = _state.value.note?.id ?: return@launch
            try {
                val opened = repo.openRevision(id, revision)
                _state.update { it.copy(note = opened, snack = "Showing revision") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun loadPermission() {
        viewModelScope.launch {
            val id = _state.value.note?.id ?: return@launch
            val known = live?.takeIf { it.noteId == id }?.permission?.value.orEmpty()
            if (known.isNotBlank()) {
                _state.update { it.copy(permission = known) }
                return@launch
            }
            try {
                _state.update { it.copy(permission = repo.currentPermission(id)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setPermission(value: String) {
        viewModelScope.launch {
            val id = _state.value.note?.id ?: return@launch
            try {
                repo.setPermission(id, value)
                _state.update { it.copy(permission = value, snack = "Permission set to $value") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun copyPublished() {
        viewModelScope.launch {
            val url = _state.value.note?.publishedUrl
            if (url.isNullOrBlank()) {
                _state.update { it.copy(error = "No published URL on this note.") }
                return@launch
            }
            _state.update { it.copy(share = OutgoingShare(text = url, mime = "text/plain", chooserTitle = "Published link")) }
        }
    }

    fun setTheme(id: String) {
        viewModelScope.launch { repo.setTheme(id) }
    }

    fun loadStatus() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(serverStatus = repo.status()) }
            } catch (e: Exception) {
                _state.update { it.copy(serverStatus = e.message.orEmpty()) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopLive()
            val theme = _state.value.themeId
            repo.disconnect()
            _state.update { UiState(ready = true, dest = Dest.Connect, themeId = theme) }
        }
    }

    fun ingestSharedText(text: String) {
        if (text.isBlank()) return
        if (_state.value.session == null) {
            _state.update { it.copy(error = "Connect to a server, then share into HedgeDoc again.") }
            return
        }
        startNew(text)
    }

    /** Leaving the editor writes whatever the debounce was still holding, before navigating. */
    private fun leaveEditor(current: Dest.Edit) {
        autosaveJob?.cancel()
        autosaveJob = null
        val outstanding = pendingEdit
        pendingEdit = null
        viewModelScope.launch {
            if (outstanding != null) {
                commit(outstanding.id, outstanding.markdown, outstanding.alias)
            }
            val id = current.id ?: _state.value.createdId
            if (id.isNullOrBlank()) {
                stopLive()
                _state.update { it.copy(dest = Dest.Notes, error = null, sync = SyncState.IDLE) }
            } else {
                _state.update { it.copy(dest = Dest.Read(id), error = null) }
            }
        }
    }

    /**
     * Writes [markdown] and settles the local copy from what we sent, never from a fresh download.
     * HedgeDoc 1.x holds the note in memory and writes it to the database later, so re-reading here
     * would hand back the version from before this save.
     */
    private suspend fun commit(id: String?, markdown: String, alias: String?) {
        val target = id ?: _state.value.createdId
        _state.update { it.copy(sync = SyncState.SAVING, error = null) }
        try {
            if (target.isNullOrBlank()) {
                if (markdown.isBlank()) {
                    _state.update { it.copy(sync = SyncState.IDLE) }
                    return
                }
                val created = repo.createNote(markdown, alias)
                val opened = repo.openNote(created, known = markdown)
                _state.update { it.copy(createdId = created, note = opened, sync = SyncState.SAVED) }
                startLive(created)
                refresh()
                return
            }
            push(target, markdown)
            repo.cacheNote(target, markdown)
            _state.update { current ->
                val note = current.note
                current.copy(
                    sync = SyncState.SAVED,
                    note = if (note != null && note.id == target) note.copy(markdown = markdown) else note,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(sync = SyncState.FAILED, error = e.message ?: "Save failed.") }
        }
    }

    /** Live socket when we have one, otherwise the plain REST/one-shot-socket write. */
    private suspend fun push(noteId: String, markdown: String) {
        val session = live?.takeIf { it.noteId == noteId }
        if (session != null && session.submit(markdown)) return
        repo.saveNote(noteId, markdown)
    }

    private fun startLive(noteId: String) {
        if (live?.noteId == noteId && liveJob?.isActive == true) return
        stopLive()
        liveJob = viewModelScope.launch {
            val session = runCatching { repo.liveSession(noteId) }.getOrNull()
            if (session == null) {
                pollForChanges(noteId)
                return@launch
            }
            live = session
            session.start()
            try {
                coroutineScope {
                    launch {
                        session.text.collect { text ->
                            if (text.isEmpty() && session.status.value != LiveStatus.LIVE) return@collect
                            _state.update { current ->
                                val note = current.note
                                if (note != null && note.id == noteId && note.markdown != text) {
                                    current.copy(note = note.copy(markdown = text, cached = false))
                                } else {
                                    current
                                }
                            }
                            repo.cacheNote(noteId, text)
                        }
                    }
                    launch {
                        session.status.collect { status ->
                            _state.update { it.copy(liveStatus = status) }
                        }
                    }
                    launch {
                        session.permission.collect { value ->
                            if (value.isNotBlank()) _state.update { it.copy(permission = value) }
                        }
                    }
                }
            } finally {
                session.stop()
                if (live === session) live = null
            }
        }
    }

    /** HedgeDoc 2 has no realtime protocol here, so the open note re-reads on a slow timer instead. */
    private suspend fun pollForChanges(noteId: String) {
        while (true) {
            delay(POLL_DELAY)
            val current = _state.value
            if (!watching(noteId, current.dest)) continue
            if (current.sync == SyncState.PENDING || current.sync == SyncState.SAVING) continue
            val fresh = runCatching { repo.openNote(noteId) }.getOrNull() ?: continue
            _state.update { latest ->
                val note = latest.note
                if (note != null && note.id == noteId && note.markdown != fresh.markdown) {
                    latest.copy(note = fresh)
                } else {
                    latest
                }
            }
        }
    }

    private fun watching(noteId: String, dest: Dest): Boolean {
        return when (dest) {
            is Dest.Read -> dest.id == noteId
            is Dest.Edit -> (dest.id ?: _state.value.createdId) == noteId
            else -> false
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        live?.stop()
        live = null
        _state.update { it.copy(liveStatus = null) }
    }

    companion object {
        private const val AUTOSAVE_DELAY = 1_200L
        private const val POLL_DELAY = 6_000L

        fun factory(repo: HedgeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
        }
    }
}
