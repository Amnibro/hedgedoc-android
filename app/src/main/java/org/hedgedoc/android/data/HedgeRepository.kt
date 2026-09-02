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
    private val net = HedgeHttp(http)
    private val api = HedgeApi(net)
    private val v2 = HedgeV2(net)
    private val cache = NoteCache(context)

    val session: Flow<Session?> = store.session
    val themeId: Flow<String> = store.themeId

    suspend fun setTheme(id: String) {
        store.setTheme(id)
    }

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
        val edition = v2.probe(server)
        val tokenMethod = method == AuthMethod.TOKEN || (edition == HedgeEdition.V2 && method == AuthMethod.COOKIE && !password.contains('='))
        val (profile, apiToken) = if (edition == HedgeEdition.V2) {
            connectV2(server, method, username, password, tokenMethod)
        } else {
            if (method == AuthMethod.TOKEN) {
                throw HedgeException("API tokens are HedgeDoc 2. This server looks like 1.x — use email, LDAP, guest, or a connect.sid cookie.")
            }
            connectV1(server, method, username, password) to ""
        }
        val session = Session(
            serverUrl = server.toString(),
            email = username.trim(),
            authMethod = method.name.lowercase(),
            profile = profile,
            edition = edition,
            apiToken = apiToken,
        )
        store.saveSession(session, cookies.snapshot())
        session
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val current = store.current()
            if (current != null) {
                if (current.edition == HedgeEdition.V2) {
                    runCatching { v2.logout(current.serverUrl.toHttpUrl()) }
                } else {
                    runCatching { api.logout(current.serverUrl.toHttpUrl()) }
                }
            }
            cookies.clear()
            store.clear()
        }
    }

    suspend fun history(): List<HistoryNote> = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.history(server, current.apiToken.ifBlank { null })
        } else {
            try {
                api.history(server)
            } catch (e: HedgeException) {
                if (v2.probe(server) == HedgeEdition.V2) {
                    persistEdition(HedgeEdition.V2)
                    v2.history(server, current.apiToken.ifBlank { null })
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.frontendStatus(server)
        } else {
            api.status(server)
        }
    }

    suspend fun openNote(noteId: String): OpenNote = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        val cached = cache.read(server.host, noteId)
        try {
            val markdown = if (current.edition == HedgeEdition.V2) {
                v2.download(server, noteId, current.apiToken.ifBlank { null })
            } else {
                api.download(server, noteId)
            }
            cache.write(server.host, noteId, markdown)
            val info = if (current.edition == HedgeEdition.V2) {
                v2.info(server, noteId, current.apiToken.ifBlank { null })
            } else {
                api.info(server, noteId)
            }
            val published = if (current.edition == HedgeEdition.V2) {
                v2.publishedUrl(server, noteId)
            } else {
                runCatching { api.publishUrl(server, noteId) }.getOrDefault("")
            }
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
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        val id = if (current.edition == HedgeEdition.V2) {
            v2.create(server, markdown, alias, current.apiToken.ifBlank { null })
        } else {
            api.create(server, markdown, alias)
        }
        cache.write(server.host, id, markdown)
        persistCookies()
        id
    }

    suspend fun saveNote(noteId: String, markdown: String) = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.save(server, noteId, markdown, current.apiToken.ifBlank { null })
        } else {
            OtClient(server, cookies.headerFor(server)).replaceContent(noteId, markdown)
        }
        cache.write(server.host, noteId, markdown)
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.deleteNote(server, noteId, current.apiToken.ifBlank { null })
        } else {
            OtClient(server, cookies.headerFor(server)).deleteNote(noteId)
        }
    }

    suspend fun setPermission(noteId: String, permission: String) = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.setPermission(server, noteId, permission, current.apiToken.ifBlank { null })
        } else {
            OtClient(server, cookies.headerFor(server)).setPermission(noteId, permission)
        }
    }

    suspend fun currentPermission(noteId: String): String = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.currentPermission(server, noteId, current.apiToken.ifBlank { null })
        } else {
            OtClient(server, cookies.headerFor(server)).currentPermission(noteId)
        }
    }

    suspend fun revisions(noteId: String): List<Revision> = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.revisions(server, noteId, current.apiToken.ifBlank { null })
        } else {
            api.revisions(server, noteId)
        }
    }

    suspend fun openRevision(noteId: String, revision: Revision): OpenNote = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        val markdown = if (current.edition == HedgeEdition.V2) {
            v2.revisionMarkdown(server, noteId, revision.id, current.apiToken.ifBlank { null })
        } else {
            api.revisionMarkdown(server, noteId, revision.time)
        }
        OpenNote(noteId, markdown, info = null, cached = false)
    }

    suspend fun pdf(noteId: String): ByteArray = withContext(Dispatchers.IO) {
        val current = requireSession()
        if (current.edition == HedgeEdition.V2) {
            throw HedgeException("HedgeDoc 2 doesn't have the 1.x PDF export. Share markdown instead.")
        }
        api.pdf(current.serverUrl.toHttpUrl(), noteId)
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.setPinned(server, noteId, pinned, current.apiToken.ifBlank { null })
        } else {
            api.setPinned(server, noteId, pinned)
        }
    }

    suspend fun removeFromHistory(noteId: String) = withContext(Dispatchers.IO) {
        val current = requireSession()
        val server = current.serverUrl.toHttpUrl()
        if (current.edition == HedgeEdition.V2) {
            v2.removeFromHistory(server, noteId, current.apiToken.ifBlank { null })
        } else {
            api.removeFromHistory(server, noteId)
        }
    }

    suspend fun noteLink(noteId: String): String {
        val current = store.current() ?: throw HedgeException("Not signed in.")
        return HedgeUrls.noteUrl(current.serverUrl.toHttpUrl(), noteId, current.edition)
    }

    private fun connectV1(
        server: HttpUrl,
        method: AuthMethod,
        username: String,
        password: String,
    ): Profile {
        return when (method) {
            AuthMethod.GUEST -> api.guest(server)
            AuthMethod.COOKIE -> {
                if (password.isBlank()) throw HedgeException("Paste a connect.sid cookie from your browser.")
                api.warmup(server)
                cookies.injectHeader(server, password)
                api.me(server) ?: api.guest(server).copy(name = "Cookie session")
            }
            AuthMethod.EMAIL, AuthMethod.LDAP -> {
                if (username.isBlank() || password.isBlank()) {
                    throw HedgeException("Email and password are both required.")
                }
                api.login(server, method, username.trim(), password)
            }
            AuthMethod.TOKEN -> throw HedgeException("API tokens are HedgeDoc 2.")
        }
    }

    private fun connectV2(
        server: HttpUrl,
        method: AuthMethod,
        username: String,
        password: String,
        asToken: Boolean,
    ): Pair<Profile, String> {
        if (asToken || method == AuthMethod.TOKEN) {
            if (password.isBlank()) throw HedgeException("Paste an API token from your HedgeDoc 2 profile.")
            val profile = v2.me(server, password)
                ?: throw HedgeException("That API token didn't work. Create one in the website profile, then paste the secret.")
            return profile to password
        }
        val profile = when (method) {
            AuthMethod.GUEST -> v2.guest(server)
            AuthMethod.COOKIE -> {
                if (password.isBlank()) throw HedgeException("Paste a session cookie from your browser.")
                cookies.injectHeader(server, password)
                v2.me(server, token = null)
                    ?: throw HedgeException("That cookie didn't get a HedgeDoc 2 session. Try an API token from your profile.")
            }
            AuthMethod.LDAP -> {
                if (username.isBlank() || password.isBlank()) {
                    throw HedgeException("Username and password are both required.")
                }
                v2.loginLdap(server, username.trim(), password)
            }
            AuthMethod.EMAIL -> {
                if (username.isBlank() || password.isBlank()) {
                    throw HedgeException("Username and password are both required.")
                }
                v2.loginLocal(server, username.trim(), password)
            }
            AuthMethod.TOKEN -> error("token handled above")
        }
        val token = if (profile.guest) null else runCatching { v2.createApiToken(server) }.getOrNull()
        return profile to token.orEmpty()
    }

    private suspend fun persistCookies() {
        val current = store.current() ?: return
        store.saveSession(current, cookies.snapshot())
    }

    private suspend fun persistEdition(edition: HedgeEdition) {
        val current = store.current() ?: return
        store.saveSession(current.copy(edition = edition), cookies.snapshot())
    }

    private suspend fun requireSession(): Session {
        return store.current() ?: throw HedgeException("Connect to a server first.")
    }
}
