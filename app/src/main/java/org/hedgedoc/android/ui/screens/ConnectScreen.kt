package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.hedgedoc.android.data.AuthMethod
import org.hedgedoc.android.ui.components.ErrorBanner
import org.hedgedoc.android.ui.theme.Frame
import org.hedgedoc.android.ui.theme.Mist
import org.hedgedoc.android.ui.theme.NightPane
import org.hedgedoc.android.ui.theme.Spine
import org.hedgedoc.android.ui.theme.Stake
import org.hedgedoc.android.ui.theme.Verdigris

@Composable
fun ConnectScreen(
    loading: Boolean,
    error: String?,
    onConnect: (server: String, method: AuthMethod, user: String, pass: String) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("https://demo.hedgedoc.org") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(AuthMethod.EMAIL) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Verdigris,
        unfocusedBorderColor = Stake,
        focusedLabelColor = Spine,
        cursorColor = Spine,
        focusedTextColor = Mist,
        unfocusedTextColor = Mist,
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Verdigris,
        selectedLabelColor = NightPane,
        containerColor = Frame,
        labelColor = Mist,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightPane)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("HedgeDoc", style = MaterialTheme.typography.displayLarge, color = Mist)
        Text(
            "Notes from the greenhouse you already run.",
            style = MaterialTheme.typography.bodyLarge,
            color = Stake,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Server") },
            placeholder = { Text("https://md.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(2.dp),
            colors = fieldColors,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = method == AuthMethod.EMAIL, onClick = { method = AuthMethod.EMAIL }, label = { Text("Email") }, colors = chipColors)
            FilterChip(selected = method == AuthMethod.LDAP, onClick = { method = AuthMethod.LDAP }, label = { Text("LDAP") }, colors = chipColors)
            FilterChip(selected = method == AuthMethod.COOKIE, onClick = { method = AuthMethod.COOKIE }, label = { Text("Cookie") }, colors = chipColors)
        }
        if (method == AuthMethod.EMAIL || method == AuthMethod.LDAP) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(if (method == AuthMethod.LDAP) "Username" else "Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
                colors = fieldColors,
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
                colors = fieldColors,
            )
        }
        if (method == AuthMethod.COOKIE) {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("connect.sid") },
                placeholder = { Text("Paste from browser dev tools") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(2.dp),
                colors = fieldColors,
            )
            Text(
                "OAuth servers don't have a password login. Copy the connect.sid cookie while you're signed in on the website.",
                style = MaterialTheme.typography.bodyMedium,
                color = Stake,
            )
        }
        if (error != null) ErrorBanner(error)
        Button(
            onClick = { onConnect(server, method, user, pass) },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Spine, contentColor = NightPane),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), color = NightPane, strokeWidth = 2.dp)
            } else {
                Text("Connect")
            }
        }
        TextButton(
            onClick = { onConnect(server, AuthMethod.GUEST, "", "") },
            enabled = !loading,
        ) {
            Text("Continue as guest", color = Verdigris)
        }
    }
}
