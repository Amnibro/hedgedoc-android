package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hedgedoc.android.data.HedgeEdition
import org.hedgedoc.android.data.Session
import org.hedgedoc.android.ui.components.ThemePicker
import org.hedgedoc.android.ui.theme.LocalScient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: Session,
    serverStatus: String,
    themeId: String,
    onTheme: (String) -> Unit,
    onBack: () -> Unit,
    onLoadStatus: () -> Unit,
    onLogout: () -> Unit,
) {
    val pal = LocalScient.current
    LaunchedEffect(session.serverUrl) { onLoadStatus() }
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            TopAppBar(
                title = { Text("Server", color = pal.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = pal.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LabelValue("Signed in as", session.profile.name.ifBlank { session.email.ifBlank { "Guest" } })
            LabelValue("Server", session.serverUrl.trimEnd('/'))
            LabelValue("HedgeDoc", if (session.edition == HedgeEdition.V2) "2" else "1.x")
            LabelValue("Method", session.authMethod)
            if (serverStatus.isNotBlank()) LabelValue("Status", serverStatus)
            Text("Theme", style = MaterialTheme.typography.labelSmall, color = pal.textSoft)
            ThemePicker(selectedId = themeId, onSelect = onTheme)
            Text(
                if (session.edition == HedgeEdition.V2) {
                    "HedgeDoc 2 saves over REST (PUT /api/v2/notes). Live editor opens /n/… with your session cookie."
                } else {
                    "New notes use POST /new. Saving an existing note replaces the whole document over Socket.IO. If someone else is typing in it, your save can overwrite them. Live editor opens the real HedgeDoc page."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSoft,
            )
            Text(
                "This is an unofficial client. HedgeDoc is AGPL. The HedgeDoc logo is not included.",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSoft,
            )
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = pal.danger, contentColor = pal.accentInk),
                shape = RoundedCornerShape(2.dp),
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    val pal = LocalScient.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = pal.textSoft)
        Text(value, style = MaterialTheme.typography.titleMedium, color = pal.text)
    }
}
