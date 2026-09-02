package org.hedgedoc.android.data

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HedgeApi(
    private val http: OkHttpClient,
) {
    fun warmup(server: HttpUrl) {
        execute(Request.Builder().url(server).get().build(), followBody = false)
    }

    fun login(server: HttpUrl, method: AuthMethod, username: String, password: String): Profile {
        warmup(server)
        val path = if (method == AuthMethod.LDAP) "auth/ldap" else "login"
        val field = if (method == AuthMethod.LDAP) "username" else "email"
        val body = FormBody.Builder()
            .add(field, username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(HedgeUrls.join(server, path))
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json, text/html")
            .build()
        execute(request, followBody = false)
        return me(server) ?: throw HedgeException("Login didn't stick. Check email, password, and that this instance allows that method.")
    }

    fun guest(server: HttpUrl): Profile {
        warmup(server)
        return me(server) ?: Profile(id = "", name = "Guest", photoUrl = "", guest = true)
    }

    fun me(server: HttpUrl): Profile? {
        val response = execute(Request.Builder().url(HedgeUrls.join(server, "me")).get().build())
        if (response.code == 403 || response.body.isBlank()) return null
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        if (json.optString("status") == "forbidden") return null
        val id = json.optString("id")
        val name = json.optString("name")
        if (id.isBlank() && name.isBlank() && json.optString("status") != "ok") return null
        return Profile(
            id = id,
            name = name.ifBlank { "You" },
            photoUrl = json.optString("photo"),
            guest = false,
        )
    }

    fun logout(server: HttpUrl) {
        runCatching {
            execute(Request.Builder().url(HedgeUrls.join(server, "logout")).get().build(), followBody = false)
        }
    }

    fun history(server: HttpUrl): List<HistoryNote> {
        val response = execute(Request.Builder().url(HedgeUrls.join(server, "history")).get().build())
        if (response.code == 403) {
            throw HedgeException("This server wants a login before it will show history.")
        }
        val json = parseObject(response.body)
        if (json.optString("status") == "forbidden") {
            throw HedgeException("This server wants a login before it will show history.")
        }
        val array = json.optJSONArray("history") ?: JSONArray()
        val notes = ArrayList<HistoryNote>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val tagsJson = item.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsJson.length()).map { tagsJson.getString(it) }
            notes.add(
                HistoryNote(
                    id = item.optString("id"),
                    title = item.optString("text").ifBlank { "Untitled" },
                    time = item.optLong("time"),
                    tags = tags,
                    pinned = item.optBoolean("pinned"),
                ),
            )
        }
        return notes.sortedWith(compareByDescending<HistoryNote> { it.pinned }.thenByDescending { it.time })
    }

    fun download(server: HttpUrl, noteId: String): String {
        val primary = execute(
            Request.Builder().url(HedgeUrls.join(server, noteId, "download")).get().build(),
        )
        if (primary.code in 200..299) return primary.body
        val published = execute(
            Request.Builder().url(HedgeUrls.join(server, "s", noteId, "download")).get().build(),
        )
        if (published.code in 200..299) return published.body
        if (primary.code == 404 || published.code == 404) throw HedgeException("Note $noteId is gone.")
        if (primary.code == 403 || published.code == 403) throw HedgeException("No permission to read this note.")
        throw HedgeException("Couldn't download $noteId (${primary.code}).")
    }

    fun info(server: HttpUrl, noteId: String): NoteInfo? {
        val response = runCatching {
            execute(Request.Builder().url(HedgeUrls.join(server, noteId, "info")).get().build())
        }.getOrNull() ?: return null
        if (response.code !in 200..299) return null
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        return NoteInfo(
            id = noteId,
            title = json.optString("title").ifBlank { "Untitled" },
            description = json.optString("description"),
            viewCount = json.optLong("viewcount"),
            createdAt = json.opt("createtime")?.toString().orEmpty(),
            updatedAt = json.opt("updatetime")?.toString().orEmpty(),
        )
    }

    fun create(server: HttpUrl, markdown: String, alias: String? = null): String {
        val url = if (alias.isNullOrBlank()) {
            HedgeUrls.join(server, "new")
        } else {
            HedgeUrls.join(server, "new", alias)
        }
        val body = markdown.toRequestBody("text/markdown; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "text/markdown")
            .build()
        val response = execute(request, followBody = false)
        val location = response.headers["Location"]
        val fromHeader = location?.let { HedgeUrls.noteIdFromUrl(it, server) }
        if (!fromHeader.isNullOrBlank()) return fromHeader
        val finalUrl = response.finalUrl
        val fromFinal = HedgeUrls.noteIdFromUrl(finalUrl, server)
        if (!fromFinal.isNullOrBlank()) return fromFinal
        throw HedgeException("Created a note but couldn't read its id from the redirect.")
    }

    fun setPinned(server: HttpUrl, noteId: String, pinned: Boolean) {
        val body = FormBody.Builder().add("pinned", pinned.toString()).build()
        execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "history", noteId))
                .post(body)
                .build(),
            followBody = false,
        )
    }

    fun removeFromHistory(server: HttpUrl, noteId: String) {
        execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "history", noteId))
                .delete()
                .build(),
            followBody = false,
        )
    }

    fun status(server: HttpUrl): String {
        val response = execute(Request.Builder().url(HedgeUrls.join(server, "status")).get().build())
        if (response.code !in 200..299) return "HTTP ${response.code}"
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (json != null) {
            val version = json.optString("version").ifBlank { json.optString("release") }
            val online = json.opt("online")
            return buildString {
                if (version.isNotBlank()) append("HedgeDoc $version")
                else append("HedgeDoc")
                if (online != null) append(if (online == true || online.toString() == "true") " · online" else " · offline")
            }
        }
        return response.body.take(80).ifBlank { "Reached ${server.host}" }
    }

    fun revisions(server: HttpUrl, noteId: String): List<Revision> {
        val response = execute(
            Request.Builder().url(HedgeUrls.join(server, noteId, "revision")).get().build(),
        )
        if (response.code !in 200..299) {
            throw HedgeException("Revisions aren't available for this note (${response.code}).")
        }
        val root = runCatching { JSONObject(response.body) }.getOrNull()
        val array = when {
            root != null -> root.optJSONArray("revision") ?: root.optJSONArray("revisions") ?: JSONArray()
            else -> runCatching { JSONArray(response.body) }.getOrDefault(JSONArray())
        }
        val out = ArrayList<Revision>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            out.add(
                Revision(
                    time = item.optLong("time"),
                    length = item.optInt("length"),
                    author = item.optString("author").ifBlank { item.optString("name") },
                ),
            )
        }
        return out.sortedByDescending { it.time }
    }

    fun revisionMarkdown(server: HttpUrl, noteId: String, timestamp: Long): String {
        val response = execute(
            Request.Builder().url(HedgeUrls.join(server, noteId, "revision", timestamp.toString())).get().build(),
        )
        if (response.code !in 200..299) {
            throw HedgeException("Couldn't load that revision.")
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (json != null) {
            val content = json.optString("content").ifBlank { json.optString("str") }
            if (content.isNotBlank()) return content
        }
        return response.body
    }

    fun pdf(server: HttpUrl, noteId: String): ByteArray {
        val request = Request.Builder()
            .url(HedgeUrls.join(server, noteId, "pdf"))
            .header("Accept", "application/pdf")
            .get()
            .build()
        val call = http.newCall(request)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw HedgeException("Couldn't download the PDF. ${e.message}", e)
        }
        response.use { res ->
            if (res.code !in 200..299) {
                throw HedgeException("PDF export failed (${res.code}). This instance may have it turned off.")
            }
            return res.body?.bytes() ?: throw HedgeException("Empty PDF.")
        }
    }

    fun publishUrl(server: HttpUrl, noteId: String): String {
        val response = execute(
            Request.Builder().url(HedgeUrls.join(server, noteId, "publish")).get().build(),
            followBody = false,
        )
        val location = response.headers["Location"]
        if (!location.isNullOrBlank()) {
            return if (location.startsWith("http")) location else HedgeUrls.join(server, location.trimStart('/')).toString()
        }
        return response.finalUrl
    }

    private data class RawResponse(
        val code: Int,
        val body: String,
        val headers: Map<String, String>,
        val finalUrl: String,
    )

    private fun execute(request: Request, followBody: Boolean = true): RawResponse {
        val call = http.newCall(request)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw HedgeException("Couldn't reach ${request.url.host}. ${e.message}", e)
        }
        response.use { res ->
            val body = if (followBody) res.body?.string().orEmpty() else ""
            val headers = buildMap {
                res.headers.names().forEach { name ->
                    val value = res.header(name)
                    if (value != null) put(name, value)
                }
            }
            if (res.code >= 500) {
                throw HedgeException("Server error ${res.code} from ${request.url.encodedPath}.")
            }
            return RawResponse(res.code, body, headers, res.request.url.toString())
        }
    }

    private fun parseObject(raw: String): JSONObject {
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw HedgeException("Server sent HTML instead of JSON. This app talks to HedgeDoc 1.x.", e)
        }
    }

    companion object {
        fun newClient(cookieJar: MemoryCookieJar): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
