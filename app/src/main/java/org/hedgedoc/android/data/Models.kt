package org.hedgedoc.android.data

data class Profile(
    val id: String,
    val name: String,
    val photoUrl: String,
    val guest: Boolean,
)

data class HistoryNote(
    val id: String,
    val title: String,
    val time: Long,
    val tags: List<String>,
    val pinned: Boolean,
)

data class NoteInfo(
    val id: String,
    val title: String,
    val description: String,
    val viewCount: Long,
    val createdAt: String,
    val updatedAt: String,
)

data class Revision(
    val id: String,
    val time: Long,
    val length: Int,
    val author: String,
)

data class Session(
    val serverUrl: String,
    val email: String,
    val authMethod: String,
    val profile: Profile,
    val edition: HedgeEdition = HedgeEdition.V1,
    val apiToken: String = "",
)

data class OpenNote(
    val id: String,
    val markdown: String,
    val info: NoteInfo?,
    val cached: Boolean,
    val permission: String = "",
    val publishedUrl: String = "",
)

data class ServerStatus(
    val summary: String,
)

data class OutgoingShare(
    val text: String? = null,
    val bytes: ByteArray? = null,
    val fileName: String? = null,
    val mime: String,
    val chooserTitle: String,
)

class HedgeException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class AuthMethod { EMAIL, LDAP, GUEST, COOKIE, TOKEN }

enum class HedgeEdition { V1, V2 }

enum class NoteFilter { ALL, PINNED }

val NotePermissions = listOf(
    "freely",
    "editable",
    "limited",
    "locked",
    "protected",
    "private",
)

val NotePermissionsV2 = listOf("public", "private")
