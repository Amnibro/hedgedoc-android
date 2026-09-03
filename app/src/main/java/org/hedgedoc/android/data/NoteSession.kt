package org.hedgedoc.android.data

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

enum class LiveStatus { CONNECTING, LIVE, OFFLINE, READONLY, GONE, FAILED }

/**
 * A Socket.IO connection to one HedgeDoc 1.x note, held open for as long as the note is on screen.
 *
 * [text] is the server document with every local edit already folded in, so a screen can render it
 * directly. Writing is the ot.js client state machine: at most one operation is in flight, later
 * local edits pile up in [buffer], and anything the server sends while we are waiting gets
 * transformed against what we have not had acknowledged yet.
 */
class NoteSession(
    private val server: HttpUrl,
    private val cookieHeader: String,
    val noteId: String,
) {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _status = MutableStateFlow(LiveStatus.CONNECTING)
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private val _permission = MutableStateFlow("")
    val permission: StateFlow<String> = _permission.asStateFlow()

    private val lock = Any()
    private var revision = 0
    private var outstanding: TextOperation? = null
    private var buffer: TextOperation? = null
    private var started = false
    private var socket: Socket? = null

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        val opts = IO.Options().apply {
            path = HedgeUrls.socketIoPath(server)
            query = "noteId=$noteId"
            forceNew = true
            reconnection = true
            reconnectionDelay = 1_000
            transports = arrayOf("websocket", "polling")
            extraHeaders = mapOf("Cookie" to listOf(cookieHeader))
        }
        val created = IO.socket(URI.create(HedgeUrls.origin(server)), opts)
        socket = created
        created.on("doc") { args -> onDoc(args.firstOrNull()) }
        created.on("operation") { args -> onRemoteOperation(args) }
        created.on("ack") { onAck() }
        created.on("permission") { args -> onPermission(args.firstOrNull()) }
        created.on("delete") { _status.value = LiveStatus.GONE }
        created.on("auth error") { _status.value = LiveStatus.FAILED }
        created.on(Socket.EVENT_CONNECT_ERROR) { _status.value = LiveStatus.OFFLINE }
        created.on(Socket.EVENT_DISCONNECT) {
            if (_status.value != LiveStatus.GONE) _status.value = LiveStatus.OFFLINE
        }
        created.connect()
    }

    fun stop() {
        val current = socket
        socket = null
        synchronized(lock) {
            started = false
            outstanding = null
            buffer = null
        }
        runCatching { current?.off() }
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
    }

    /**
     * Send the note as it now reads. Diffed against the document this session already knows about,
     * so a one-character edit is a one-character operation.
     */
    fun submit(newText: String): Boolean {
        if (_status.value != LiveStatus.LIVE) return false
        val outgoing = synchronized(lock) {
            val base = _text.value
            if (base == newText) return true
            val edit = TextOperation.diff(base, newText)
            if (edit.isNoop()) return true
            _text.value = newText
            val pending = outstanding
            if (pending == null) {
                outstanding = edit
                edit to revision
            } else {
                val existing = buffer
                buffer = if (existing == null) edit else existing.compose(edit)
                null
            }
        }
        if (outgoing != null) send(outgoing.first, outgoing.second)
        return true
    }

    private fun onDoc(payload: Any?) {
        val json = when (payload) {
            is JSONObject -> payload
            is String -> runCatching { JSONObject(payload) }.getOrNull()
            else -> null
        } ?: return
        synchronized(lock) {
            revision = json.optInt("revision")
            outstanding = null
            buffer = null
            _status.value = LiveStatus.LIVE
            _text.value = json.optString("str")
        }
    }

    private fun onRemoteOperation(args: Array<out Any?>) {
        val raw = args.getOrNull(1)
        val array = when (raw) {
            is JSONArray -> raw
            is String -> runCatching { JSONArray(raw) }.getOrNull()
            else -> null
        } ?: return
        val incoming = runCatching {
            TextOperation.fromJsonList((0 until array.length()).map { array.get(it) })
        }.getOrNull() ?: return
        synchronized(lock) {
            revision++
            val pending = outstanding
            if (pending == null) {
                _text.value = runCatching { incoming.apply(_text.value) }.getOrElse { return }
                return
            }
            val (pendingPrime, afterPending) = runCatching {
                TextOperation.transform(pending, incoming)
            }.getOrElse { return }
            val held = buffer
            if (held == null) {
                outstanding = pendingPrime
                _text.value = runCatching { afterPending.apply(_text.value) }.getOrElse { return }
                return
            }
            val (bufferPrime, afterBuffer) = runCatching {
                TextOperation.transform(held, afterPending)
            }.getOrElse { return }
            outstanding = pendingPrime
            buffer = bufferPrime
            _text.value = runCatching { afterBuffer.apply(_text.value) }.getOrElse { return }
        }
    }

    private fun onAck() {
        val next = synchronized(lock) {
            revision++
            val held = buffer
            buffer = null
            outstanding = held
            if (held == null) null else held to revision
        }
        if (next != null) send(next.first, next.second)
    }

    private fun onPermission(payload: Any?) {
        val value = when (payload) {
            is String -> payload
            is JSONObject -> payload.optString("permission")
            else -> payload?.toString().orEmpty()
        }
        _permission.value = value
        if (value in setOf("locked", "private", "protected")) {
            _status.value = LiveStatus.READONLY
        } else if (_status.value == LiveStatus.READONLY) {
            _status.value = LiveStatus.LIVE
        }
    }

    /**
     * [basedOn] is captured with the operation, not read here. A remote operation arriving in
     * between would move the revision, and the server transforms what we send against the revision
     * we say it was built on.
     */
    private fun send(op: TextOperation, basedOn: Int) {
        val target = socket ?: return
        val payload = JSONArray()
        op.toJsonList().forEach { part -> payload.put(part) }
        val caret = caretAfter(op)
        val selection = JSONObject().put(
            "ranges",
            JSONArray().put(JSONObject().put("anchor", caret).put("head", caret)),
        )
        target.emit("operation", basedOn, payload, selection)
    }

    /** Where the caret lands after [op]: just past the last thing it actually changed. */
    private fun caretAfter(op: TextOperation): Int {
        var caret = 0
        var position = 0
        for (part in op.parts) {
            when (part) {
                is Int -> if (part > 0) position += part else caret = position
                is String -> {
                    position += part.length
                    caret = position
                }
            }
        }
        return caret
    }
}
