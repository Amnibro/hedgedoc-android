package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import org.hedgedoc.android.data.HedgeEdition
import org.hedgedoc.android.data.HistoryNote
import org.hedgedoc.android.data.NotePermissions
import org.hedgedoc.android.data.NotePermissionsV2
import org.hedgedoc.android.data.OpenNote
import org.hedgedoc.android.data.Revision
import org.hedgedoc.android.ui.components.ErrorBanner
import org.hedgedoc.android.ui.components.MarkdownPane
import org.hedgedoc.android.ui.theme.LocalScient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    note: OpenNote?,
    history: HistoryNote?,
    revisions: List<Revision>,
    permission: String,
    edition: HedgeEdition?,
    loading: Boolean,
    error: String?,
    serverUrl: String,
    http: OkHttpClient,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLive: () -> Unit,
    onShare: () -> Unit,
    onShareMarkdown: () -> Unit,
    onSharePdf: () -> Unit,
    onPublished: () -> Unit,
    onPin: () -> Unit,
    onForget: () -> Unit,
    onDelete: () -> Unit,
    onLoadRevisions: () -> Unit,
    onOpenRevision: (Revision) -> Unit,
    onLoadPermission: () -> Unit,
    onSetPermission: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var revMenu by remember { mutableStateOf(false) }
    var permMenu by remember { mutableStateOf(false) }
    val pal = LocalScient.current
    val title = note?.info?.title ?: history?.title ?: note?.id ?: "Note"
    val meta = buildString {
        note?.info?.let { info ->
            if (info.viewCount > 0) append("${info.viewCount} views")
            if (info.description.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(info.description)
            }
        }
        if (note?.cached == true) {
            if (isNotEmpty()) append(" · ")
            append("cached copy")
        }
    }
    Scaffold(
        containerColor = pal.paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = pal.text, maxLines = 1)
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = pal.accent, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = pal.text)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = pal.text)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = pal.text)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Live editor") }, onClick = { menu = false; onLive() })
                            DropdownMenuItem(text = { Text("Share link") }, onClick = { menu = false; onShare() })
                            DropdownMenuItem(text = { Text("Share markdown") }, onClick = { menu = false; onShareMarkdown() })
                            if (edition != HedgeEdition.V2) {
                                DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menu = false; onSharePdf() })
                            }
                            DropdownMenuItem(text = { Text("Published link") }, onClick = { menu = false; onPublished() })
                            DropdownMenuItem(text = { Text(if (history?.pinned == true) "Unpin" else "Pin") }, onClick = { menu = false; onPin() })
                            DropdownMenuItem(
                                text = { Text("Revisions") },
                                onClick = {
                                    menu = false
                                    onLoadRevisions()
                                    revMenu = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Permission") },
                                onClick = {
                                    menu = false
                                    onLoadPermission()
                                    permMenu = true
                                },
                            )
                            DropdownMenuItem(text = { Text("Remove from history") }, onClick = { menu = false; onForget() })
                            DropdownMenuItem(text = { Text("Delete note") }, onClick = { menu = false; onDelete() })
                        }
                        DropdownMenu(expanded = revMenu, onDismissRequest = { revMenu = false }) {
                            if (revisions.isEmpty()) {
                                DropdownMenuItem(text = { Text("No revisions yet") }, onClick = { revMenu = false })
                            } else {
                                revisions.take(20).forEach { revision ->
                                    val stamp = if (revision.time > 0) {
                                        SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(revision.time))
                                    } else {
                                        revision.time.toString()
                                    }
                                    DropdownMenuItem(
                                        text = { Text("$stamp · ${revision.length} chars") },
                                        onClick = { revMenu = false; onOpenRevision(revision) },
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = permMenu, onDismissRequest = { permMenu = false }) {
                            val options = if (edition == HedgeEdition.V2) NotePermissionsV2 else NotePermissions
                            options.forEach { value ->
                                val mark = if (value == permission) " · current" else ""
                                DropdownMenuItem(
                                    text = { Text(value + mark) },
                                    onClick = { permMenu = false; onSetPermission(value) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(pal.paper),
        ) {
            when {
                error != null && note == null -> ErrorBanner(error, modifier = Modifier.padding(16.dp))
                loading && note == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = pal.accent)
                note != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        MarkdownPane(
                            markdown = note.markdown,
                            baseUrl = serverUrl,
                            http = http,
                            textColor = pal.paperInk.toArgb(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
