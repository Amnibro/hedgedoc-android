package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.OkHttpClient
import org.hedgedoc.android.data.LiveStatus
import org.hedgedoc.android.ui.SyncState
import org.hedgedoc.android.ui.components.ErrorBanner
import org.hedgedoc.android.ui.components.MarkdownPane
import org.hedgedoc.android.ui.theme.LocalScient
import org.hedgedoc.android.ui.theme.MonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    editKey: String,
    noteId: String?,
    seed: String,
    sync: SyncState,
    liveStatus: LiveStatus?,
    remoteText: String?,
    error: String?,
    serverUrl: String,
    http: OkHttpClient,
    onBack: () -> Unit,
    onChange: (markdown: String, alias: String?) -> Unit,
) {
    var text by rememberSaveable(editKey) { mutableStateOf(seed) }
    var alias by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    val pal = LocalScient.current
    val settled = sync == SyncState.IDLE || sync == SyncState.SAVED
    LaunchedEffect(remoteText, settled) {
        if (settled && remoteText != null && remoteText != text) text = remoteText
    }
    val title = if (noteId == null) "New note" else "Edit"
    Scaffold(
        containerColor = pal.bg,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = pal.text)
                        Text(
                            "${text.length} characters · ${syncLabel(sync, liveStatus)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sync == SyncState.FAILED) pal.accent else pal.textSoft,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = pal.text)
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            if (preview) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (preview) "Show markdown" else "Preview",
                            tint = pal.text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (error != null) {
                ErrorBanner(error, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (noteId == null) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = {
                        alias = it
                        onChange(text, it.ifBlank { null })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = { Text("Alias (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = pal.accent,
                        unfocusedBorderColor = pal.border,
                        cursorColor = pal.accent,
                        focusedTextColor = pal.text,
                        unfocusedTextColor = pal.text,
                    ),
                )
            }
            if (preview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                        .background(pal.paper)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    MarkdownPane(
                        markdown = text,
                        baseUrl = serverUrl,
                        http = http,
                        textColor = pal.paperInk.toArgb(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onChange(it, alias.ifBlank { null })
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    textStyle = TextStyle(
                        fontFamily = MonoFont,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = pal.text,
                    ),
                    cursorBrush = SolidColor(pal.accent),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "# Title\n\nWrite markdown. It saves itself.",
                                    style = TextStyle(
                                        fontFamily = MonoFont,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = pal.textSoft,
                                    ),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

private fun syncLabel(sync: SyncState, liveStatus: LiveStatus?): String {
    if (liveStatus == LiveStatus.READONLY) return "read only"
    if (liveStatus == LiveStatus.GONE) return "note deleted"
    return when (sync) {
        SyncState.IDLE -> if (liveStatus == LiveStatus.OFFLINE) "offline" else "up to date"
        SyncState.PENDING -> "saving soon"
        SyncState.SAVING -> "saving"
        SyncState.SAVED -> "saved"
        SyncState.FAILED -> "save failed"
    }
}
