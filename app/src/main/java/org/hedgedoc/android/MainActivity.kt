package org.hedgedoc.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.hedgedoc.android.data.HedgeEdition
import org.hedgedoc.android.data.HedgeUrls
import org.hedgedoc.android.ui.AppViewModel
import org.hedgedoc.android.ui.Dest
import org.hedgedoc.android.ui.screens.ConnectScreen
import org.hedgedoc.android.ui.screens.EditorScreen
import org.hedgedoc.android.ui.screens.LiveEditorScreen
import org.hedgedoc.android.ui.screens.NotesScreen
import org.hedgedoc.android.ui.screens.ReaderScreen
import org.hedgedoc.android.ui.screens.SettingsScreen
import org.hedgedoc.android.ui.theme.HedgeTheme
import org.hedgedoc.android.ui.theme.LocalScient
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        AppViewModel.factory((application as HedgeApp).repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ingest(intent)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            HedgeTheme(themeId = state.themeId) {
                val snackHost = remember { SnackbarHostState() }
                LaunchedEffect(state.share) {
                    val share = state.share ?: return@LaunchedEffect
                    val send = Intent(Intent.ACTION_SEND)
                    if (share.bytes != null) {
                        val file = File(cacheDir, share.fileName ?: "note.bin")
                        file.writeBytes(share.bytes)
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.files",
                            file,
                        )
                        send.type = share.mime
                        send.putExtra(Intent.EXTRA_STREAM, uri)
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        send.type = share.mime
                        send.putExtra(Intent.EXTRA_TEXT, share.text)
                    }
                    startActivity(Intent.createChooser(send, share.chooserTitle))
                    vm.consumeShare()
                }
                LaunchedEffect(state.snack) {
                    val snack = state.snack ?: return@LaunchedEffect
                    snackHost.showSnackbar(snack)
                    vm.consumeSnack()
                }
                Box(Modifier.fillMaxSize()) {
                    if (!state.ready) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = LocalScient.current.accent)
                    } else {
                        val canBack = state.dest !is Dest.Connect && state.dest !is Dest.Notes
                        BackHandler(enabled = canBack) { vm.back() }
                        when (val dest = state.dest) {
                            Dest.Connect -> ConnectScreen(
                                loading = state.loading,
                                error = state.error,
                                themeId = state.themeId,
                                onTheme = vm::setTheme,
                                onConnect = vm::connect,
                            )
                            Dest.Notes -> {
                                val session = state.session
                                if (session == null) {
                                    ConnectScreen(
                                        loading = state.loading,
                                        error = state.error,
                                        themeId = state.themeId,
                                        onTheme = vm::setTheme,
                                        onConnect = vm::connect,
                                    )
                                } else {
                                    NotesScreen(
                                        session = session,
                                        notes = state.filtered,
                                        query = state.query,
                                        filter = state.filter,
                                        loading = state.loading,
                                        error = state.error,
                                        onQuery = vm::setQuery,
                                        onFilter = vm::setFilter,
                                        onOpen = { vm.open(it.id) },
                                        onPin = vm::pin,
                                        onForget = { vm.forget(it.id) },
                                        onShare = { vm.shareLink(it.id) },
                                        onDelete = { vm.destroy(it.id) },
                                        onRefresh = vm::refresh,
                                        onSettings = { vm.go(Dest.Settings) },
                                        onCreate = { vm.startNew() },
                                    )
                                }
                            }
                            is Dest.Read -> ReaderScreen(
                                note = state.note,
                                history = state.notes.find { it.id == dest.id },
                                revisions = state.revisions,
                                permission = state.permission,
                                edition = state.session?.edition,
                                loading = state.loading,
                                liveStatus = state.liveStatus,
                                sync = state.sync,
                                error = state.error,
                                serverUrl = state.session?.serverUrl.orEmpty(),
                                http = vm.http,
                                onBack = vm::back,
                                onToggleTask = vm::toggleTask,
                                onEdit = vm::startEdit,
                                onLive = { vm.openLive(dest.id) },
                                onShare = vm::shareLink,
                                onShareMarkdown = vm::shareMarkdown,
                                onSharePdf = vm::sharePdf,
                                onPublished = vm::copyPublished,
                                onPin = { state.notes.find { it.id == dest.id }?.let(vm::pin) },
                                onForget = { vm.forget(dest.id) },
                                onDelete = { vm.destroy(dest.id) },
                                onLoadRevisions = vm::loadRevisions,
                                onOpenRevision = vm::openRevision,
                                onLoadPermission = vm::loadPermission,
                                onSetPermission = vm::setPermission,
                            )
                            is Dest.Edit -> EditorScreen(
                                editKey = dest.id ?: "new",
                                noteId = dest.id ?: state.createdId,
                                seed = dest.seed,
                                sync = state.sync,
                                liveStatus = state.liveStatus,
                                remoteText = state.note?.takeIf { it.id == (dest.id ?: state.createdId) }?.markdown,
                                error = state.error,
                                serverUrl = state.session?.serverUrl.orEmpty(),
                                http = vm.http,
                                onBack = vm::back,
                                onChange = { markdown, alias -> vm.edit(dest.id, markdown, alias) },
                            )
                            is Dest.Live -> {
                                val server = state.session?.serverUrl.orEmpty()
                                val parsed = server.toHttpUrlOrNull()
                                val url = if (parsed != null) {
                                    HedgeUrls.noteUrl(parsed, dest.id, state.session?.edition ?: HedgeEdition.V1)
                                } else {
                                    server
                                }
                                LiveEditorScreen(
                                    url = url,
                                    cookieHeader = vm.cookieHeader(),
                                    onBack = vm::back,
                                )
                            }
                            Dest.Settings -> {
                                val session = state.session
                                if (session == null) {
                                    ConnectScreen(
                                        loading = state.loading,
                                        error = state.error,
                                        themeId = state.themeId,
                                        onTheme = vm::setTheme,
                                        onConnect = vm::connect,
                                    )
                                } else {
                                    SettingsScreen(
                                        session = session,
                                        serverStatus = state.serverStatus,
                                        themeId = state.themeId,
                                        onTheme = vm::setTheme,
                                        onBack = vm::back,
                                        onLoadStatus = vm::loadStatus,
                                        onLogout = vm::logout,
                                    )
                                }
                            }
                        }
                    }
                    SnackbarHost(
                        hostState = snackHost,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ingest(intent)
    }

    private fun ingest(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        vm.ingestSharedText(text)
    }
}
