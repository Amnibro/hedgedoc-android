package org.hedgedoc.android.data

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class HedgeV2(
    private val net: HedgeHttp,
) {
    fun probe(server: HttpUrl): HedgeEdition {
        val response = runCatching {
            net.execute(Request.Builder().url(HedgeUrls.join(server, "api", "private", "config")).get().build())
        }.getOrNull() ?: return HedgeEdition.V1
        return HedgeV2Parse.editionFromConfig(response.code, response.body)
    }

    fun frontendStatus(server: HttpUrl): String {
        val response = net.execute(Request.Builder().url(HedgeUrls.join(server, "api", "private", "config")).get().build())
        if (response.code !in 200..299) return "HedgeDoc 2"
        return HedgeV2Parse.statusFromConfig(response.body)
    }

    fun ldapIdentifier(server: HttpUrl): String? {
        val response = net.execute(Request.Builder().url(HedgeUrls.join(server, "api", "private", "config")).get().build())
        if (response.code !in 200..299) return null
        return HedgeV2Parse.ldapIdentifier(response.body)
    }

    fun loginLocal(server: HttpUrl, username: String, password: String): Profile {
        mutate(
            server,
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "private", "auth", "local", "login"))
                .post(jsonBody(JSONObject().put("username", username).put("password", password)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json"),
        ).also { res ->
            if (res.code == 401) throw HedgeException("Invalid username or password.")
            if (res.code !in 200..299) throw HedgeException("Login failed (${res.code}).")
        }
        return me(server, token = null) ?: throw HedgeException("Login didn't stick.")
    }

    fun loginLdap(server: HttpUrl, username: String, password: String): Profile {
        val identifier = ldapIdentifier(server)
            ?: throw HedgeException("This HedgeDoc 2 server didn't advertise an LDAP provider.")
        mutate(
            server,
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "private", "auth", "ldap", identifier, "login"))
                .post(jsonBody(JSONObject().put("username", username).put("password", password)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json"),
        ).also { res ->
            if (res.code == 401) throw HedgeException("Invalid LDAP username or password.")
            if (res.code !in 200..299) throw HedgeException("LDAP login failed (${res.code}).")
        }
        return me(server, token = null) ?: throw HedgeException("LDAP login didn't stick.")
    }

    fun guest(server: HttpUrl): Profile {
        val res = mutate(
            server,
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "private", "auth", "guest", "register"))
                .post(jsonBody(JSONObject()))
                .header("Content-Type", "application/json"),
        )
        if (res.code == 403) throw HedgeException("Guests are turned off on this HedgeDoc 2 server.")
        if (res.code !in 200..299) throw HedgeException("Guest login failed (${res.code}).")
        return me(server, token = null) ?: Profile(id = "", name = "Guest", photoUrl = "", guest = true)
    }

    fun me(server: HttpUrl, token: String?): Profile? {
        val private = net.execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "private", "me"))
                .get()
                .auth(token)
                .build(),
        )
        if (private.code in 200..299) {
            return HedgeV2Parse.profile(private.body)
        }
        if (!token.isNullOrBlank()) {
            val public = net.execute(
                Request.Builder()
                    .url(HedgeUrls.join(server, "api", "v2", "me"))
                    .get()
                    .auth(token)
                    .build(),
            )
            if (public.code in 200..299) return HedgeV2Parse.profile(public.body)
        }
        return null
    }

    fun createApiToken(server: HttpUrl): String? {
        val until = Instant.now().atOffset(ZoneOffset.UTC).plusYears(10)
            .format(DateTimeFormatter.ISO_INSTANT)
        val res = mutate(
            server,
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "private", "tokens"))
                .post(jsonBody(JSONObject().put("label", "HedgeDoc Android").put("validUntil", until)))
                .header("Content-Type", "application/json"),
        )
        if (res.code !in 200..299) return null
        return HedgeV2Parse.tokenSecret(res.body)
    }

    fun history(server: HttpUrl, token: String?): List<HistoryNote> {
        val urls = listOf(
            HedgeUrls.join(server, "api", "private", "me", "history"),
            HedgeUrls.join(server, "api", "v2", "me", "history"),
            HedgeUrls.join(server, "api", "v2", "me", "notes"),
            HedgeUrls.join(server, "api", "private", "me", "notes"),
        )
        for (url in urls) {
            val res = net.execute(Request.Builder().url(url).get().auth(token).build())
            if (res.code in 200..299 && res.body.isNotBlank() && looksJson(res.body)) {
                val notes = HedgeV2Parse.history(res.body)
                if (notes.isNotEmpty() || res.body.trim() == "[]" || res.body.contains("\"history\"")) {
                    return notes
                }
            }
        }
        throw HedgeException("Couldn't load notes from this HedgeDoc 2 server.")
    }

    fun download(server: HttpUrl, noteId: String, token: String?): String {
        val content = net.execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "v2", "notes", noteId, "content"))
                .get()
                .auth(token)
                .build(),
        )
        if (content.code in 200..299 && !looksJsonObject(content.body)) {
            return content.body
        }
        val dto = getNote(server, noteId, token)
        val markdown = HedgeV2Parse.noteContent(dto)
        if (markdown != null) return markdown
        throw HedgeException("Couldn't download $noteId (${content.code}).")
    }

    fun info(server: HttpUrl, noteId: String, token: String?): NoteInfo? {
        val meta = net.execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "v2", "notes", noteId, "metadata"))
                .get()
                .auth(token)
                .build(),
        )
        if (meta.code in 200..299) return HedgeV2Parse.noteInfo(noteId, meta.body)
        val dto = runCatching { getNote(server, noteId, token) }.getOrNull() ?: return null
        return HedgeV2Parse.noteInfo(noteId, dto)
    }

    fun create(server: HttpUrl, markdown: String, alias: String?, token: String?): String {
        val url = if (alias.isNullOrBlank()) {
            HedgeUrls.join(server, "api", "v2", "notes")
        } else {
            HedgeUrls.join(server, "api", "v2", "notes", alias)
        }
        var res = net.execute(
            Request.Builder()
                .url(url)
                .post(markdown.toRequestBody(markdownType))
                .header("Content-Type", "text/markdown")
                .auth(token)
                .build(),
        )
        if (res.code == 401 || res.code == 404) {
            val privateUrl = if (alias.isNullOrBlank()) {
                HedgeUrls.join(server, "api", "private", "notes")
            } else {
                HedgeUrls.join(server, "api", "private", "notes", alias)
            }
            res = mutate(
                server,
                Request.Builder()
                    .url(privateUrl)
                    .post(markdown.toRequestBody(markdownType))
                    .header("Content-Type", "text/markdown"),
            )
        }
        if (res.code !in 200..299) throw HedgeException("Couldn't create a note (${res.code}).")
        val id = HedgeV2Parse.noteIdFromDto(res.body)
        if (id.isNullOrBlank()) throw HedgeException("Created a note but the server didn't return an id.")
        return id
    }

    fun save(server: HttpUrl, noteId: String, markdown: String, token: String?) {
        var res = net.execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "v2", "notes", noteId))
                .put(markdown.toRequestBody(markdownType))
                .header("Content-Type", "text/markdown")
                .auth(token)
                .build(),
        )
        if (res.code == 401 || res.code == 404 || res.code == 405) {
            res = mutate(
                server,
                Request.Builder()
                    .url(HedgeUrls.join(server, "api", "private", "notes", noteId))
                    .put(markdown.toRequestBody(markdownType))
                    .header("Content-Type", "text/markdown"),
            )
        }
        if (res.code !in 200..299) {
            throw HedgeException("Couldn't save this note (${res.code}).")
        }
    }

    fun deleteNote(server: HttpUrl, noteId: String, token: String?) {
        val body = jsonBody(JSONObject().put("keepMedia", false))
        var res = net.execute(
            Request.Builder()
                .url(HedgeUrls.join(server, "api", "v2", "notes", noteId))
                .delete(body)
                .header("Content-Type", "application/json")
                .auth(token)
                .build(),
        )
        if (res.code == 401 || res.code == 404) {
            res = mutate(
                server,
                Request.Builder()
                    .url(HedgeUrls.join(server, "api", "private", "notes", noteId))
                    .delete(body)
                    .header("Content-Type", "application/json"),
            )
        }
        if (res.code !in 200..299 && res.code != 204) {
            throw HedgeException("Couldn't delete this note (${res.code}). You probably need to own it.")
        }
    }

    fun setPinned(server: HttpUrl, noteId: String, pinned: Boolean, token: String?) {
        val payload = jsonBody(JSONObject().put("pinStatus", pinned).put("pinned", pinned))
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "me", "history", noteId),
            HedgeUrls.join(server, "api", "private", "me", "history", noteId),
        )
        var last = 0
        for (url in urls) {
            val builder = Request.Builder().url(url).put(payload).header("Content-Type", "application/json")
            val res = if (url.encodedPath.contains("/api/private/")) {
                mutate(server, builder)
            } else {
                net.execute(builder.auth(token).build())
            }
            last = res.code
            if (res.code in 200..299) return
        }
        throw HedgeException("This HedgeDoc 2 server doesn't expose history pin ($last).")
    }

    fun removeFromHistory(server: HttpUrl, noteId: String, token: String?) {
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "me", "history", noteId),
            HedgeUrls.join(server, "api", "private", "me", "history", noteId),
        )
        var last = 0
        for (url in urls) {
            val builder = Request.Builder().url(url).delete()
            val res = if (url.encodedPath.contains("/api/private/")) {
                mutate(server, builder)
            } else {
                net.execute(builder.auth(token).build())
            }
            last = res.code
            if (res.code in 200..299 || res.code == 204) return
        }
        throw HedgeException("Couldn't remove that note from history ($last).")
    }

    fun revisions(server: HttpUrl, noteId: String, token: String?): List<Revision> {
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "notes", noteId, "revisions"),
            HedgeUrls.join(server, "api", "private", "notes", noteId, "revisions"),
        )
        for (url in urls) {
            val res = net.execute(Request.Builder().url(url).get().auth(token).build())
            if (res.code in 200..299) return HedgeV2Parse.revisions(res.body)
        }
        throw HedgeException("Revisions aren't available for this note.")
    }

    fun revisionMarkdown(server: HttpUrl, noteId: String, revisionId: String, token: String?): String {
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "notes", noteId, "revisions", revisionId),
            HedgeUrls.join(server, "api", "private", "notes", noteId, "revisions", revisionId),
        )
        for (url in urls) {
            val res = net.execute(Request.Builder().url(url).get().auth(token).build())
            if (res.code in 200..299) {
                val content = HedgeV2Parse.noteContent(res.body)
                if (!content.isNullOrBlank()) return content
                if (!looksJsonObject(res.body)) return res.body
            }
        }
        throw HedgeException("Couldn't load that revision.")
    }

    fun publishedUrl(server: HttpUrl, noteId: String): String {
        return HedgeUrls.join(server, "s", noteId).toString()
    }

    fun currentPermission(server: HttpUrl, noteId: String, token: String?): String {
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "notes", noteId, "metadata", "permissions"),
            HedgeUrls.join(server, "api", "private", "notes", noteId, "metadata", "permissions"),
            HedgeUrls.join(server, "api", "v2", "notes", noteId, "metadata"),
        )
        for (url in urls) {
            val res = net.execute(Request.Builder().url(url).get().auth(token).build())
            if (res.code in 200..299) {
                val value = HedgeV2Parse.visibility(res.body)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    fun setPermission(server: HttpUrl, noteId: String, permission: String, token: String?) {
        val visible = permission == "public"
        val payload = jsonBody(
            JSONObject()
                .put("publiclyVisible", visible)
                .put("newPubliclyVisible", visible),
        )
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "notes", noteId, "metadata", "permissions", "visibility"),
            HedgeUrls.join(server, "api", "private", "notes", noteId, "metadata", "permissions", "visibility"),
        )
        var last = 0
        for (url in urls) {
            val builder = Request.Builder().url(url).put(payload).header("Content-Type", "application/json")
            val res = if (url.encodedPath.contains("/api/private/")) {
                mutate(server, builder)
            } else {
                net.execute(builder.auth(token).build())
            }
            last = res.code
            if (res.code in 200..299) return
        }
        throw HedgeException("Couldn't change visibility ($last).")
    }

    fun logout(server: HttpUrl) {
        runCatching {
            mutate(
                server,
                Request.Builder().url(HedgeUrls.join(server, "api", "private", "auth", "logout")).delete(),
            )
        }
    }

    private fun getNote(server: HttpUrl, noteId: String, token: String?): String {
        val urls = listOf(
            HedgeUrls.join(server, "api", "v2", "notes", noteId),
            HedgeUrls.join(server, "api", "private", "notes", noteId),
        )
        for (url in urls) {
            val res = net.execute(Request.Builder().url(url).get().auth(token).build())
            if (res.code in 200..299) return res.body
        }
        throw HedgeException("Note $noteId is gone.")
    }

    private fun csrf(server: HttpUrl): String {
        val res = net.execute(
            Request.Builder().url(HedgeUrls.join(server, "api", "private", "csrf", "token")).get().build(),
        )
        if (res.code !in 200..299) return ""
        return HedgeV2Parse.csrfToken(res.body)
    }

    private fun mutate(server: HttpUrl, builder: Request.Builder): HedgeHttp.RawResponse {
        val token = csrf(server)
        if (token.isNotBlank()) builder.header("csrf-token", token)
        return net.execute(builder.build())
    }

    private fun Request.Builder.auth(token: String?): Request.Builder {
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        return this
    }

    private fun jsonBody(json: JSONObject) = json.toString().toRequestBody(jsonType)

    private fun looksJson(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun looksJsonObject(raw: String) = raw.trim().startsWith("{")

    companion object {
        private val jsonType = "application/json; charset=utf-8".toMediaType()
        private val markdownType = "text/markdown; charset=utf-8".toMediaType()
    }
}

object HedgeV2Parse {
    fun editionFromConfig(code: Int, body: String): HedgeEdition {
        if (code !in 200..299) return HedgeEdition.V1
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return HedgeEdition.V1
        if (json.has("authProviders") || json.has("version") || json.has("guestAccess")) {
            return HedgeEdition.V2
        }
        return HedgeEdition.V1
    }

    fun statusFromConfig(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return "HedgeDoc 2"
        val version = json.opt("version")
        val formatted = when (version) {
            is JSONObject -> listOf(
                version.opt("major"),
                version.opt("minor"),
                version.opt("patch"),
            ).joinToString(".").trim('.')
            else -> version?.toString().orEmpty()
        }.trim('.')
        val name = json.optJSONObject("branding")?.optString("name").orEmpty()
        return buildString {
            append("HedgeDoc 2")
            if (formatted.isNotBlank() && formatted != "null") append(" $formatted")
            if (name.isNotBlank()) append(" · $name")
        }
    }

    fun ldapIdentifier(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val providers = json.optJSONArray("authProviders") ?: return null
        for (i in 0 until providers.length()) {
            val item = providers.optJSONObject(i) ?: continue
            val type = item.optString("type").lowercase()
            if (type == "ldap") {
                return item.optString("identifier").ifBlank { "ldap" }
            }
        }
        return null
    }

    fun csrfToken(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        return json.optString("token").ifBlank { json.optString("csrfToken") }
    }

    fun profile(body: String): Profile? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val username = json.optString("username")
        val display = json.optString("displayName").ifBlank { json.optString("name") }
        if (username.isBlank() && display.isBlank()) return null
        val provider = json.optString("authProvider").ifBlank { json.optString("authProviderType") }
        return Profile(
            id = json.opt("id")?.toString().orEmpty().ifBlank { username },
            name = display.ifBlank { username }.ifBlank { "You" },
            photoUrl = json.optString("photoUrl").ifBlank { json.optString("photo") },
            guest = provider.equals("guest", ignoreCase = true),
        )
    }

    fun tokenSecret(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val secret = json.optString("secret").ifBlank { json.optString("token") }
        return secret.ifBlank { null }
    }

    fun history(body: String): List<HistoryNote> {
        val array = when {
            body.trim().startsWith("[") -> runCatching { JSONArray(body) }.getOrDefault(JSONArray())
            else -> {
                val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
                json.optJSONArray("history")
                    ?: json.optJSONArray("notes")
                    ?: JSONArray()
            }
        }
        val notes = ArrayList<HistoryNote>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val meta = item.optJSONObject("metadata") ?: item
            val id = meta.optString("identifier")
                .ifBlank { aliasOf(meta) }
                .ifBlank { item.optString("id") }
            if (id.isBlank()) continue
            notes.add(
                HistoryNote(
                    id = id,
                    title = meta.optString("title").ifBlank { item.optString("title").ifBlank { item.optString("text") } }
                        .ifBlank { "Untitled" },
                    time = parseTime(
                        meta.opt("lastVisitedAt")
                            ?: meta.opt("editedAt")
                            ?: meta.opt("updatedAt")
                            ?: item.opt("time"),
                    ),
                    tags = tagsOf(meta).ifEmpty { tagsOf(item) },
                    pinned = item.optBoolean("pinStatus") || item.optBoolean("pinned") || meta.optBoolean("pinStatus"),
                ),
            )
        }
        return notes.sortedWith(compareByDescending<HistoryNote> { it.pinned }.thenByDescending { it.time })
    }

    fun noteIdFromDto(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val meta = json.optJSONObject("metadata") ?: json
        return aliasOf(meta).ifBlank { json.optString("id") }.ifBlank { null }
    }

    fun noteContent(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val content = json.optString("content").ifBlank { json.optString("markdown") }
        return content.ifBlank { null }
    }

    fun noteInfo(noteId: String, body: String): NoteInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val meta = json.optJSONObject("metadata") ?: json
        val id = aliasOf(meta).ifBlank { noteId }
        return NoteInfo(
            id = id,
            title = meta.optString("title").ifBlank { "Untitled" },
            description = meta.optString("description"),
            viewCount = meta.optLong("viewCount").takeIf { it > 0 } ?: meta.optLong("viewcount"),
            createdAt = meta.opt("createdAt")?.toString().orEmpty(),
            updatedAt = (meta.opt("editedAt") ?: meta.opt("updatedAt") ?: meta.opt("updateTime"))?.toString().orEmpty(),
        )
    }

    fun revisions(body: String): List<Revision> {
        val array = when {
            body.trim().startsWith("[") -> runCatching { JSONArray(body) }.getOrDefault(JSONArray())
            else -> {
                val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
                json.optJSONArray("revisions") ?: JSONArray()
            }
        }
        val out = ArrayList<Revision>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("uuid").ifBlank { item.optString("id").ifBlank { item.opt("time")?.toString().orEmpty() } }
            val time = parseTime(item.opt("createdAt") ?: item.opt("time"))
            out.add(
                Revision(
                    id = id.ifBlank { time.toString() },
                    time = time,
                    length = item.optInt("length"),
                    author = item.optString("author").ifBlank {
                        val names = item.optJSONArray("authorUsernames")
                        if (names != null && names.length() > 0) names.optString(0) else ""
                    },
                ),
            )
        }
        return out.sortedByDescending { it.time }
    }

    fun visibility(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        val meta = json.optJSONObject("metadata") ?: json
        if (meta.has("publiclyVisible")) {
            return if (meta.optBoolean("publiclyVisible")) "public" else "private"
        }
        val permissions = meta.optJSONObject("permissions") ?: json.optJSONObject("permissions")
        if (permissions != null && permissions.has("publiclyVisible")) {
            return if (permissions.optBoolean("publiclyVisible")) "public" else "private"
        }
        return ""
    }

    fun parseTime(raw: Any?): Long {
        if (raw == null || raw == JSONObject.NULL) return 0L
        when (raw) {
            is Number -> {
                val n = raw.toLong()
                return if (n > 0 && n < 1_000_000_000_000L) n * 1000 else n
            }
        }
        val text = raw.toString().trim()
        if (text.isEmpty()) return 0L
        text.toLongOrNull()?.let { n ->
            return if (n > 0 && n < 1_000_000_000_000L) n * 1000 else n
        }
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
    }

    private fun aliasOf(meta: JSONObject): String {
        val primary = meta.optString("primaryAlias").ifBlank { meta.optString("primaryAddress") }
        if (primary.isNotBlank()) return primary
        val aliases = meta.optJSONArray("aliases") ?: return meta.optString("id").ifBlank { meta.optString("identifier") }
        for (i in 0 until aliases.length()) {
            val obj = aliases.optJSONObject(i)
            if (obj != null && obj.optBoolean("primary")) {
                return obj.optString("name").ifBlank { obj.optString("id") }
            }
        }
        val firstObj = aliases.optJSONObject(0)
        if (firstObj != null) return firstObj.optString("name").ifBlank { firstObj.optString("id") }
        return aliases.optString(0).ifBlank { meta.optString("id") }
    }

    private fun tagsOf(json: JSONObject): List<String> {
        val tags = json.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length()).mapNotNull { index ->
            val obj = tags.optJSONObject(index)
            val name = if (obj != null) obj.optString("name") else tags.optString(index)
            name.takeIf { it.isNotBlank() }
        }
    }
}
