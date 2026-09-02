package org.hedgedoc.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

private val Context.sessionDataStore by preferencesDataStore(name = "hedgedoc_session")

class SessionStore(private val context: Context) {
    private val keyServer = stringPreferencesKey("server")
    private val keyEmail = stringPreferencesKey("email")
    private val keyAuth = stringPreferencesKey("auth")
    private val keyProfile = stringPreferencesKey("profile")
    private val keyCookies = stringPreferencesKey("cookies")

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val server = prefs[keyServer].orEmpty()
        val profileRaw = prefs[keyProfile].orEmpty()
        if (server.isBlank() || profileRaw.isBlank()) {
            null
        } else {
            Session(
                serverUrl = server,
                email = prefs[keyEmail].orEmpty(),
                authMethod = prefs[keyAuth].orEmpty().ifBlank { "email" },
                profile = decodeProfile(profileRaw),
            )
        }
    }

    suspend fun current(): Session? = session.first()

    suspend fun saveSession(session: Session, cookies: List<StoredCookie>) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyServer] = session.serverUrl
            prefs[keyEmail] = session.email
            prefs[keyAuth] = session.authMethod
            prefs[keyProfile] = encodeProfile(session.profile)
            prefs[keyCookies] = encodeCookies(cookies)
        }
    }

    suspend fun loadCookies(): List<StoredCookie> {
        val raw = context.sessionDataStore.data.first()[keyCookies].orEmpty()
        if (raw.isBlank()) return emptyList()
        return decodeCookies(raw)
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private fun encodeProfile(profile: Profile): String {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("photo", profile.photoUrl)
            .put("guest", profile.guest)
            .toString()
    }

    private fun decodeProfile(raw: String): Profile {
        val json = JSONObject(raw)
        return Profile(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { if (json.optBoolean("guest")) "Guest" else "You" },
            photoUrl = json.optString("photo"),
            guest = json.optBoolean("guest"),
        )
    }

    private fun encodeCookies(cookies: List<StoredCookie>): String {
        val array = JSONArray()
        cookies.forEach { cookie ->
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly),
            )
        }
        return array.toString()
    }

    private fun decodeCookies(raw: String): List<StoredCookie> {
        val array = JSONArray(raw)
        val out = ArrayList<StoredCookie>(array.length())
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            out.add(
                StoredCookie(
                    name = json.getString("name"),
                    value = json.getString("value"),
                    domain = json.getString("domain"),
                    path = json.optString("path", "/"),
                    expiresAt = json.optLong("expiresAt", Long.MAX_VALUE / 2),
                    secure = json.optBoolean("secure"),
                    httpOnly = json.optBoolean("httpOnly"),
                    hostOnly = json.optBoolean("hostOnly", true),
                ),
            )
        }
        return out
    }
}

data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toOkHttp(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path(path.ifBlank { "/" })
            .expiresAt(expiresAt)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}

class MemoryCookieJar : CookieJar {
    private val lock = Any()
    private val store = LinkedHashMap<String, Cookie>()

    fun snapshot(): List<StoredCookie> = synchronized(lock) {
        store.values.map { StoredCookie.from(it) }
    }

    fun replaceAll(cookies: List<StoredCookie>) {
        synchronized(lock) {
            store.clear()
            cookies.forEach { stored ->
                store[key(stored.name, stored.domain, stored.path)] = stored.toOkHttp()
            }
        }
    }

    fun headerFor(url: HttpUrl): String {
        return loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun injectHeader(url: HttpUrl, raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val pieces = if (trimmed.contains('=') && !trimmed.startsWith("s:")) {
            trimmed.split(';').map { it.trim() }.filter { it.contains('=') }
        } else {
            listOf("connect.sid=$trimmed")
        }
        pieces.forEach { piece ->
            val cookie = Cookie.parse(url, piece)
                ?: Cookie.parse(url, piece.substringBefore(';'))
            if (cookie != null) {
                saveFromResponse(url, listOf(cookie))
            } else {
                val name = piece.substringBefore('=').trim()
                val value = piece.substringAfter('=').trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    val built = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .hostOnlyDomain(url.host)
                        .path("/")
                        .expiresAt(Long.MAX_VALUE / 2)
                    if (url.isHttps) built.secure()
                    saveFromResponse(url, listOf(built.build()))
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) { store.clear() }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            cookies.forEach { cookie ->
                store[key(cookie.name, cookie.domain, cookie.path)] = cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val matches = store.values.filter { it.expiresAt > now && it.matches(url) }
            return matches
        }
    }

    private fun key(name: String, domain: String, path: String) = "$name|$domain|$path"
}
