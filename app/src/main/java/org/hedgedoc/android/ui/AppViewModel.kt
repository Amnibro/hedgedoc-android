package org.hedgedoc.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import org.hedgedoc.android.data.NoteFilter
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

data class UiState(
    val ready: Boolean = false,
    val dest: Dest = Dest.Connect,
    val session: Session? = null,
    val notes: List<HistoryNote> = emptyList(),
    val query: String = "",
    val filter: NoteFilter = NoteFilter.ALL,
    val loading: Boolean = false,
    val saving: Boolean = false,
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

    fun consumeShare() { _state.update { it.copy(share = null) } }
    fun consumeSnack() { _state.update { it.copy(snack = null) } }

    fun go(dest: Dest) {
        _state.update { it.copy(dest = dest, error = null) }
    }

    fun back() {
        val dest = when (val current = _state.value.dest) {
            is Dest.Live -> Dest.Read(current.id)
            is Dest.Read, is Dest.Edit, Dest.Settings -> Dest.Notes
            else -> current
        }
        _state.update { it.copy(dest = dest, error = null) }
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
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, dest = Dest.Read(noteId), revisions = emptyList()) }
            try {
                val note = repo.openNote(noteId)
                _state.update { it.copy(note = note) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not open note.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun startNew(seed: String = "") {
        _state.update { it.copy(dest = Dest.Edit(null, seed), note = null, error = null) }
    }

    fun startEdit() {
        val note = _state.value.note ?: return
        _state.update { it.copy(dest = Dest.Edit(note.id, note.markdown), error = null) }
    }

    fun openLive(noteId: String = _state.value.note?.id.orEmpty()) {
        if (noteId.isBlank()) return
        _state.update { it.copy(dest = Dest.Live(noteId), error = null) }
    }

    fun save(id: String?, markdown: String, alias: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val noteId = if (id.isNullOrBlank()) {
                    repo.createNote(markdown, alias)
                } else {
                    repo.saveNote(id, markdown)
                    id
                }
                val opened = repo.openNote(noteId)
                refresh()
                _state.update {
                    it.copy(
                        dest = Dest.Read(noteId),
                        note = opened,
                        snack = if (id.isNullOrBlank()) "Created" else "Saved",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Save failed.") }
            } finally {
                _state.update { it.copy(saving = false) }
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

    companion object {
        fun factory(repo: HedgeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
        }
    }
}
