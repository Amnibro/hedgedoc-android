package org.hedgedoc.android.data

import android.content.Context
import java.io.File

class NoteCache(context: Context) {
    private val root = File(context.filesDir, "notes").apply { mkdirs() }

    fun write(serverHost: String, noteId: String, markdown: String) {
        val file = fileFor(serverHost, noteId)
        file.parentFile?.mkdirs()
        file.writeText(markdown)
    }

    fun read(serverHost: String, noteId: String): String? {
        val file = fileFor(serverHost, noteId)
        if (!file.exists()) return null
        return file.readText()
    }

    fun clear() {
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun fileFor(serverHost: String, noteId: String): File {
        val safeHost = serverHost.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeId = noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(File(root, safeHost), "$safeId.md")
    }
}
