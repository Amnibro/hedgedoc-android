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
import org.hedgedoc.android.data.Session
import org.hedgedoc.android.ui.theme.Blot
import org.hedgedoc.android.ui.theme.Mist
import org.hedgedoc.android.ui.theme.NightPane
import org.hedgedoc.android.ui.theme.Stake

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: Session,
    serverStatus: String,
    onBack: () -> Unit,
    onLoadStatus: () -> Unit,
    onLogout: () -> Unit,
) {
    LaunchedEffect(session.serverUrl) { onLoadStatus() }
    Scaffold(
        containerColor = NightPane,
        topBar = {
            TopAppBar(
                title = { Text("Server", color = Mist) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mist)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightPane),
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
            LabelValue("Method", session.authMethod)
            if (serverStatus.isNotBlank()) LabelValue("Status", serverStatus)
            Text(
                "New notes use POST /new. Saving an existing note replaces the whole document over Socket.IO. If someone else is typing in it, your save can overwrite them. Live editor opens the real HedgeDoc page.",
                style = MaterialTheme.typography.bodyMedium,
                color = Stake,
            )
            Text(
                "This is an unofficial client. HedgeDoc is AGPL. The HedgeDoc logo is not included.",
                style = MaterialTheme.typography.bodyMedium,
                color = Stake,
            )
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Blot),
                shape = RoundedCornerShape(2.dp),
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Stake)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Mist)
    }
}
