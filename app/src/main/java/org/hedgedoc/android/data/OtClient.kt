package org.hedgedoc.android.data

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HedgeDoc 1.x edits notes over Socket.IO using ShareJS operations.
 * This client replaces the whole document in one operation.
 */
class OtClient(
    private val server: HttpUrl,
    private val cookieHeader: String,
) {
    suspend fun replaceContent(noteId: String, newContent: String) {
        withTimeout(25_000) {
            withSocket(noteId) { socket, current, revision ->
                if (current == newContent) return@withSocket
                val op = TextOperation.replaceAll(current, newContent)
                val payload = JSONArray()
                op.toJsonList().forEach { value -> payload.put(value) }
                val selection = JSONObject().put(
                    "ranges",
                    JSONArray().put(
                        JSONObject()
                            .put("anchor", newContent.length)
                            .put("head", newContent.length),
                    ),
                )
                suspendCancellableCoroutine { cont ->
                    socket.once("ack") {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    socket.once("permission") { args ->
                        val perm = args.firstOrNull()?.toString().orEmpty()
                        if (perm in setOf("locked", "private", "protected") && cont.isActive) {
                            cont.resumeWithException(
                                HedgeException("This note is $perm, so it can't be saved from the app."),
                            )
                        }
                    }
                    socket.emit("operation", revision, payload, selection)
                }
            }
        }
    }

    suspend fun deleteNote(noteId: String) {
        withTimeout(15_000) {
            withSocket(noteId) { socket, _, _ ->
                suspendCancellableCoroutine { cont ->
                    socket.once("delete") {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    socket.emit("delete")
                    socket.once(Socket.EVENT_DISCONNECT) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
    }

    suspend fun setPermission(noteId: String, permission: String) {
        withTimeout(12_000) {
            withSocket(noteId) { socket, _, _ ->
                suspendCancellableCoroutine { cont ->
                    socket.once("permission") {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    socket.emit("permission", permission)
                }
            }
        }
    }

    suspend fun currentPermission(noteId: String): String {
        return withTimeout(12_000) {
            withSocket(noteId) { socket, _, _ ->
                suspendCancellableCoroutine { cont ->
                    socket.once("refresh") { args ->
                        val json = args.firstOrNull() as? JSONObject
                        val perm = json?.optString("permission").orEmpty()
                        if (cont.isActive) cont.resume(perm)
                    }
                    socket.emit("refresh")
                }
            }
        }
    }

    private suspend fun <T> withSocket(
        noteId: String,
        block: suspend (Socket, String, Int) -> T,
    ): T {
        val opts = IO.Options().apply {
            path = HedgeUrls.socketIoPath(server)
            query = "noteId=$noteId"
            forceNew = true
            reconnection = false
            transports = arrayOf("websocket", "polling")
            extraHeaders = mapOf("Cookie" to listOf(cookieHeader))
        }
        val socket = IO.socket(URI.create(HedgeUrls.origin(server)), opts)
        return try {
            val doc = suspendCancellableCoroutine<Pair<String, Int>> { cont ->
                socket.once("doc") { args ->
                    val data = args.firstOrNull()
                    val json = when (data) {
                        is JSONObject -> data
                        is String -> runCatching { JSONObject(data) }.getOrNull()
                        else -> null
                    }
                    val str = json?.optString("str").orEmpty()
                    val revision = json?.optInt("revision") ?: 0
                    if (cont.isActive) cont.resume(str to revision)
                }
                socket.once("auth error") {
                    if (cont.isActive) {
                        cont.resumeWithException(HedgeException("Socket auth failed for this note."))
                    }
                }
                socket.once(Socket.EVENT_CONNECT_ERROR) { args ->
                    if (cont.isActive) {
                        cont.resumeWithException(
                            HedgeException(args.firstOrNull()?.toString() ?: "Socket failed to connect."),
                        )
                    }
                }
                cont.invokeOnCancellation { socket.disconnect() }
                socket.connect()
            }
            block(socket, doc.first, doc.second)
        } finally {
            runCatching { socket.disconnect() }
            runCatching { socket.close() }
        }
    }
}
