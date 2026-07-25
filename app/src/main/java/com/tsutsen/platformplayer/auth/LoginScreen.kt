/*
 * Login Screen (Compose)
 *
 * A Compose-based login screen that opens a WebView for plugin authentication.
 * Monitors network requests for auth cookies/headers and returns SourceAuth when complete.
 */

package com.tsutsen.platformplayer.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tsutsen.platformplayer.api.media.platforms.js.SourceAuth
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.others.LoginWebViewClient
import kotlinx.coroutines.launch

private const val TAG = "LoginScreen"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    config: SourcePluginConfig,
    onLogin: (SourceAuth?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val webViewClient = remember { LoginWebViewClient(config) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(webViewClient) {
        webViewClient.onLogin.subscribe { auth ->
            Logger.i(TAG, "Login successful for ${config.name}")
            onLogin(auth)
        }
    }

    // Load the login URL once the WebView is ready
    LaunchedEffect(webViewRef.value) {
        val webView = webViewRef.value ?: return@LaunchedEffect
        try {
            val loginUrl = config.authentication?.loginUrl ?: config.sourceUrl
                ?: throw IllegalStateException("No login URL configured for ${config.name}")
            
            Logger.i(TAG, "Loading login URL: $loginUrl")
            webView.loadUrl(loginUrl)
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
            Logger.e(TAG, "Failed to load login URL", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login to ${config.name}") },
                navigationIcon = {
                    IconButton(onClick = {
                        Logger.i(TAG, "Login cancelled by user")
                        onLogin(null)
                        onBack()
                    }) {
                        Text("✕")
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Show warning if present
                config.authentication?.loginWarning?.let { warning ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = warning,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // WebView for login
                AndroidView(
                    factory = { ctx ->
                        val webView = WebView(ctx)
                        webView.settings.javaScriptEnabled = true
                        webView.settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webView.webViewClient = webViewClient
                        webView.settings.useWideViewPort = true
                        webView.settings.loadWithOverviewMode = true

                        // Set custom user agent if configured
                        config.authentication?.userAgent?.let { ua ->
                            webView.settings.userAgentString = ua
                        }

                        // Store reference for URL loading
                        webViewRef.value = webView
                        webView
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { webView ->
                        webView.destroy()
                    }
                )

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    )
}
