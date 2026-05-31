package com.aiexile.animetrack.ui.login

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.auth.AuthManager

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) {
            webView?.destroy()
            webView = null
            onLoginSuccess()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            webView?.destroy()
            webView = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "登录 Bangumi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(
                        onClick = {
                            webView?.destroy()
                            webView = null
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "正在登录...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                } else if (uiState.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.error ?: "登录失败",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = {
                                webView?.reload()
                            }) {
                                Text("重试")
                            }
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            CookieManager.getInstance().removeAllCookies(null)
                            WebView(ctx).apply {
                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return handleRedirect(url)
                                    }

                                    @Deprecated("Deprecated in API")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {
                                        return handleRedirect(url ?: return false)
                                    }

                                    private fun handleRedirect(url: String): Boolean {
                                        if (url.startsWith(AuthManager.REDIRECT_URI)) {
                                            val uri = Uri.parse(url)
                                            val code = uri.getQueryParameter("code")
                                            if (code != null) {
                                                viewModel.fetchAccessToken(code)
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }

                                loadUrl(AuthManager.AUTH_URL)
                            }.also { webView = it }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
