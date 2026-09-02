package org.hedgedoc.android.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object HedgeUrls {
    private val reserved = setOf(
        "", "new", "login", "logout", "me", "history", "auth", "status",
        "config", "features", "s", "p", "uploads", "api", "socket.io",
        "build", "css", "js", "fonts", "screenshot", "user", "register",
        "pretty", "privacy", "terms", "favicon.ico", "robots.txt",
    )

    fun normalizeServer(raw: String): HttpUrl {
        var value = raw.trim()
        if (value.isEmpty()) {
            throw HedgeException("Enter a server URL.")
        }
        if (!value.contains("://")) {
            value = "https://$value"
        }
        val parsed = value.toHttpUrlOrNull()
            ?: throw HedgeException("That doesn't look like a URL.")
        val builder = parsed.newBuilder().encodedQuery(null).fragment(null)
        val path = parsed.encodedPath.trimEnd('/')
        builder.encodedPath(if (path.isEmpty()) "/" else "$path/")
        return builder.build()
    }

    fun join(server: HttpUrl, vararg segments: String): HttpUrl {
        val builder = server.newBuilder()
        segments.forEach { part ->
            part.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        }
        return builder.build()
    }

    fun origin(server: HttpUrl): String {
        val port = if (server.isHttps) {
            if (server.port == 443) "" else ":${server.port}"
        } else {
            if (server.port == 80) "" else ":${server.port}"
        }
        return "${server.scheme}://${server.host}$port"
    }

    fun socketIoPath(server: HttpUrl): String {
        val path = server.encodedPath.trimEnd('/')
        return if (path.isEmpty() || path == "/") "/socket.io/" else "$path/socket.io/"
    }

    fun noteIdFromPath(path: String): String? {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val first = parts[0]
        if (first.lowercase() == "s" && parts.size >= 2) {
            return parts[1].substringBefore('?').substringBefore('#')
        }
        if (first.lowercase() in reserved) return null
        return first.substringBefore('?').substringBefore('#')
    }

    fun noteIdFromUrl(url: String, server: HttpUrl? = null): String? {
        val parsed = url.trim().toHttpUrlOrNull() ?: return noteIdFromPath(url.trim())
        if (server != null && parsed.host != server.host) {
            return noteIdFromPath(parsed.encodedPath)
        }
        val basePath = server?.encodedPath?.trim('/') ?: ""
        var path = parsed.encodedPath.trim('/')
        if (basePath.isNotEmpty() && path.startsWith(basePath)) {
            path = path.removePrefix(basePath).trim('/')
        }
        return noteIdFromPath(path)
    }

    fun noteUrl(server: HttpUrl, noteId: String): String = join(server, noteId).toString()

    fun titleFromMarkdown(markdown: String): String {
        val lines = markdown.lineSequence()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("---") || trimmed.startsWith("title:", ignoreCase = true)) {
                if (trimmed.startsWith("title:", ignoreCase = true)) {
                    val yaml = trimmed.substringAfter(':').trim().trim('"', '\'')
                    if (yaml.isNotEmpty()) return yaml
                }
                continue
            }
            return trimmed.removePrefix("#").trim().ifBlank { "Untitled" }
        }
        return "Untitled"
    }
}
