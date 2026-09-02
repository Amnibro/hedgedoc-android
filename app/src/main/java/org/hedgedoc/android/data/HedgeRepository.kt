package org.hedgedoc.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class HedgeRepository(context: Context) {
    private val store = SessionStore(context)
    private val cookies = MemoryCookieJar()
    val http = HedgeApi.newClient(cookies)
    private val api = HedgeApi(http)
    private val cache = NoteCache(context)

    val session: Flow<Session?> = store.session

    suspend fun restoreCookies() {
        cookies.replaceAll(store.loadCookies())
    }

    fun cookieHeader(serverUrl: String): String {
        return cookies.headerFor(serverUrl.toHttpUrl())
    }

    suspend fun connect(
        serverRaw: String,
        method: AuthMethod,
        username: String,
        password: String,
    ): Session = withContext(Dispatchers.IO) {
        cookies.clear()
        val server = HedgeUrls.normalizeServer(serverRaw)
        val profile = when (method) {
            AuthMethod.GUEST -> api.guest(server)
            AuthMethod.COOKIE -> {
                if (password.isBlank()) throw HedgeException("Paste a connect.sid cookie from your browser.")
                api.warmup(server)
                cookies.injectHeader(server, password)
                api.me(server)
                    ?: api.guest(server).copy(name = "Cookie session")
            }
            AuthMethod.EMAIL, AuthMethod.LDAP -> {
                if (username.isBlank() || password.isBlank()) {
                    throw HedgeException("Email and password are both required.")
                }
                api.login(server, method, username.trim(), password)
            }
        }
        val session = Session(
            serverUrl = server.toString(),
            email = username.trim(),
            authMethod = method.name.lowercase(),
            profile = profile,
        )
        store.saveSession(session, cookies.snapshot())
        session
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val current = store.current()
            if (current != null) {
                runCatching { api.logout(current.serverUrl.toHttpUrl()) }
            }
            cookies.clear()
            store.clear()
        }
    }

    suspend fun history(): List<HistoryNote> = withContext(Dispatchers.IO) {
        api.history(requireServer())
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        api.status(requireServer())
    }

    suspend fun openNote(noteId: String): OpenNote = withContext(Dispatchers.IO) {
        val server = requireServer()
        val cached = cache.read(server.host, noteId)
        try {
            val markdown = api.download(server, noteId)
            cache.write(server.host, noteId, markdown)
            val info = api.info(server, noteId)
            val published = runCatching { api.publishUrl(server, noteId) }.getOrDefault("")
            OpenNote(noteId, markdown, info, cached = false, publishedUrl = published)
        } catch (e: Exception) {
            if (cached != null) {
                OpenNote(noteId, cached, info = null, cached = true)
            } else {
                throw e
            }
        }
    }

    suspend fun createNote(markdown: String, alias: String? = null): String = withContext(Dispatchers.IO) {
        val server = requireServer()
        val id = api.create(server, markdown, alias)
        cache.write(server.host, id, markdown)
        persistCookies()
        id
    }

    suspend fun saveNote(noteId: String, markdown: String) = withContext(Dispatchers.IO) {
        val server = requireServer()
        OtClient(server, cookies.headerFor(server)).replaceContent(noteId, markdown)
        cache.write(server.host, noteId, markdown)
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        val server = requireServer()
        OtClient(server, cookies.headerFor(server)).deleteNote(noteId)
    }

    suspend fun setPermission(noteId: String, permission: String) = withContext(Dispatchers.IO) {
        val server = requireServer()
        OtClient(server, cookies.headerFor(server)).setPermission(noteId, permission)
    }

    suspend fun currentPermission(noteId: String): String = withContext(Dispatchers.IO) {
        val server = requireServer()
        OtClient(server, cookies.headerFor(server)).currentPermission(noteId)
    }

    suspend fun revisions(noteId: String): List<Revision> = withContext(Dispatchers.IO) {
        api.revisions(requireServer(), noteId)
    }

    suspend fun openRevision(noteId: String, timestamp: Long): OpenNote = withContext(Dispatchers.IO) {
        val markdown = api.revisionMarkdown(requireServer(), noteId, timestamp)
        OpenNote(noteId, markdown, info = null, cached = false)
    }

    suspend fun pdf(noteId: String): ByteArray = withContext(Dispatchers.IO) {
        api.pdf(requireServer(), noteId)
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        api.setPinned(requireServer(), noteId, pinned)
    }

    suspend fun removeFromHistory(noteId: String) = withContext(Dispatchers.IO) {
        api.removeFromHistory(requireServer(), noteId)
    }

    suspend fun noteLink(noteId: String): String {
        val current = store.current() ?: throw HedgeException("Not signed in.")
        return HedgeUrls.noteUrl(current.serverUrl.toHttpUrl(), noteId)
    }

    private suspend fun persistCookies() {
        val current = store.current() ?: return
        store.saveSession(current, cookies.snapshot())
    }

    private suspend fun requireServer(): HttpUrl {
        val current = store.current() ?: throw HedgeException("Connect to a server first.")
        return current.serverUrl.toHttpUrl()
    }
}
