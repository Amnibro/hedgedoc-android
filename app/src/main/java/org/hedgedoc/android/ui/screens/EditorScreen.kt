package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import org.hedgedoc.android.ui.components.ErrorBanner
import org.hedgedoc.android.ui.components.MarkdownPane
import org.hedgedoc.android.ui.theme.Ink
import org.hedgedoc.android.ui.theme.Label
import org.hedgedoc.android.ui.theme.Mist
import org.hedgedoc.android.ui.theme.MonoFont
import org.hedgedoc.android.ui.theme.NightPane
import org.hedgedoc.android.ui.theme.Spine
import org.hedgedoc.android.ui.theme.Stake
import org.hedgedoc.android.ui.theme.Verdigris

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String?,
    seed: String,
    saving: Boolean,
    error: String?,
    serverUrl: String,
    http: OkHttpClient,
    onBack: () -> Unit,
    onSave: (markdown: String, alias: String?) -> Unit,
) {
    var text by rememberSaveable(noteId, seed) { mutableStateOf(seed) }
    var alias by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    val title = if (noteId == null) "New note" else "Edit"
    Scaffold(
        containerColor = NightPane,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Mist)
                        Text(
                            "${text.length} characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = Stake,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mist)
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            if (preview) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (preview) "Show markdown" else "Preview",
                            tint = Mist,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightPane),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!saving) onSave(text, alias.ifBlank { null }) },
                containerColor = Spine,
                contentColor = NightPane,
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "Save")
            }
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
                    onValueChange = { alias = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = { Text("Alias (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Verdigris,
                        unfocusedBorderColor = Stake,
                        cursorColor = Spine,
                        focusedTextColor = Mist,
                        unfocusedTextColor = Mist,
                    ),
                )
            }
            if (preview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 88.dp)
                        .background(Label)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    MarkdownPane(
                        markdown = text,
                        baseUrl = serverUrl,
                        http = http,
                        textColor = Ink.toArgb(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 88.dp),
                    textStyle = TextStyle(
                        fontFamily = MonoFont,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Mist,
                    ),
                    cursorBrush = SolidColor(Spine),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "# Title\n\nWrite markdown. Save writes the note on your server.",
                                    style = TextStyle(
                                        fontFamily = MonoFont,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = Stake,
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
