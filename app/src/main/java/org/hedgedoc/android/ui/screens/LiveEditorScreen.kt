package org.hedgedoc.android.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.hedgedoc.android.ui.theme.LocalScient

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEditorScreen(
    url: String,
    cookieHeader: String,
    onBack: () -> Unit,
) {
    val pal = LocalScient.current
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            TopAppBar(
                title = { Text("Live editor", color = pal.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = pal.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    val manager = CookieManager.getInstance()
                    manager.setAcceptCookie(true)
                    manager.setAcceptThirdPartyCookies(this, true)
                    cookieHeader.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { piece ->
                        manager.setCookie(url, piece)
                    }
                    manager.flush()
                    loadUrl(url)
                }
            },
            onRelease = { view -> view.destroy() },
        )
    }
}
