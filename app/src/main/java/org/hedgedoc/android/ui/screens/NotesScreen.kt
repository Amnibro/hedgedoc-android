package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hedgedoc.android.data.HistoryNote
import org.hedgedoc.android.data.NoteFilter
import org.hedgedoc.android.data.Session
import org.hedgedoc.android.ui.components.ErrorBanner
import org.hedgedoc.android.ui.components.NoteRow
import org.hedgedoc.android.ui.theme.LocalScient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    session: Session,
    notes: List<HistoryNote>,
    query: String,
    filter: NoteFilter,
    loading: Boolean,
    error: String?,
    onQuery: (String) -> Unit,
    onFilter: (NoteFilter) -> Unit,
    onOpen: (HistoryNote) -> Unit,
    onPin: (HistoryNote) -> Unit,
    onForget: (HistoryNote) -> Unit,
    onShare: (HistoryNote) -> Unit,
    onDelete: (HistoryNote) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onCreate: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    val pal = LocalScient.current
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = pal.accent,
        selectedLabelColor = pal.accentInk,
        containerColor = pal.panel,
        labelColor = pal.text,
    )
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes", style = MaterialTheme.typography.headlineMedium, color = pal.text)
                        Text(
                            session.profile.name.ifBlank { session.email.ifBlank { session.serverUrl } },
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.textSoft,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = pal.text)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = pal.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = pal.accent,
                contentColor = pal.accentInk,
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search or paste a note URL") },
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
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == NoteFilter.ALL,
                    onClick = { onFilter(NoteFilter.ALL) },
                    label = { Text("All") },
                    colors = chipColors,
                )
                FilterChip(
                    selected = filter == NoteFilter.PINNED,
                    onClick = { onFilter(NoteFilter.PINNED) },
                    label = { Text("Pinned") },
                    colors = chipColors,
                )
            }
            if (error != null) {
                ErrorBanner(error, modifier = Modifier.padding(top = 12.dp))
            }
            if (notes.isEmpty() && !loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        if (session.profile.guest) {
                            "Guest history is empty. Paste a public note URL, or write a new one."
                        } else {
                            "No notes in history yet. Write one."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = pal.textSoft,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        Box {
                            NoteRow(
                                note = note,
                                onClick = { onOpen(note) },
                                onMenu = { menuFor = note.id },
                            )
                            DropdownMenu(
                                expanded = menuFor == note.id,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(text = { Text(if (note.pinned) "Unpin" else "Pin") }, onClick = { menuFor = null; onPin(note) })
                                DropdownMenuItem(text = { Text("Share link") }, onClick = { menuFor = null; onShare(note) })
                                DropdownMenuItem(text = { Text("Remove from history") }, onClick = { menuFor = null; onForget(note) })
                                DropdownMenuItem(text = { Text("Delete note") }, onClick = { menuFor = null; onDelete(note) })
                            }
                        }
                    }
                }
            }
        }
    }
}
