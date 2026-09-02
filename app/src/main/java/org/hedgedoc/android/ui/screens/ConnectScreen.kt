package org.hedgedoc.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import org.hedgedoc.android.ui.components.ThemePicker
import org.hedgedoc.android.ui.theme.LocalScient

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    loading: Boolean,
    error: String?,
    themeId: String,
    onTheme: (String) -> Unit,
    onConnect: (server: String, method: AuthMethod, user: String, pass: String) -> Unit,
) {
    val pal = LocalScient.current
    var server by rememberSaveable { mutableStateOf("https://demo.hedgedoc.org") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(AuthMethod.EMAIL) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = pal.accent,
        unfocusedBorderColor = pal.border,
        focusedLabelColor = pal.accent,
        cursorColor = pal.accent,
        focusedTextColor = pal.text,
        unfocusedTextColor = pal.text,
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = pal.accent,
        selectedLabelColor = pal.accentInk,
        containerColor = pal.panel,
        labelColor = pal.text,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("HedgeDoc", style = MaterialTheme.typography.displayLarge, color = pal.text)
        Text(
            "Connects to the HedgeDoc you already run, 1.x or 2.",
            style = MaterialTheme.typography.bodyLarge,
            color = pal.textSoft,
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = method == AuthMethod.EMAIL, onClick = { method = AuthMethod.EMAIL }, label = { Text("Local") }, colors = chipColors)
            FilterChip(selected = method == AuthMethod.LDAP, onClick = { method = AuthMethod.LDAP }, label = { Text("LDAP") }, colors = chipColors)
            FilterChip(selected = method == AuthMethod.COOKIE, onClick = { method = AuthMethod.COOKIE }, label = { Text("Cookie") }, colors = chipColors)
            FilterChip(selected = method == AuthMethod.TOKEN, onClick = { method = AuthMethod.TOKEN }, label = { Text("Token") }, colors = chipColors)
        }
        if (method == AuthMethod.EMAIL || method == AuthMethod.LDAP) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(if (method == AuthMethod.LDAP) "Username" else "Email or username") },
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
                "OAuth or OIDC: copy a session cookie while you're signed in on the website. HedgeDoc 1 uses connect.sid. HedgeDoc 2 uses whatever cookie the backend set.",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSoft,
            )
        }
        if (method == AuthMethod.TOKEN) {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("API token") },
                placeholder = { Text("From your HedgeDoc 2 profile") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(2.dp),
                colors = fieldColors,
            )
            Text(
                "HedgeDoc 2 only. Create a token on the website profile page and paste the secret.",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSoft,
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
            colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.accentInk),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), color = pal.accentInk, strokeWidth = 2.dp)
            } else {
                Text("Connect")
            }
        }
        TextButton(
            onClick = { onConnect(server, AuthMethod.GUEST, "", "") },
            enabled = !loading,
        ) {
            Text("Continue as guest", color = pal.accent)
        }
        Text("Theme", style = MaterialTheme.typography.labelSmall, color = pal.textSoft)
        ThemePicker(selectedId = themeId, onSelect = onTheme)
    }
}
