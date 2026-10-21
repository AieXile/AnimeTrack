package com.aiexile.animetrack.ui.settings

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.login.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiLoginScreen(
    onBack: () -> Unit,
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
        }
    }

    AppScaffold(
        title = stringResource(R.string.bangumi_login_title),
        onBack = {
            // 与原返回逻辑一致：先销毁 WebView 再返回
            webView?.destroy()
            webView = null
            onBack()
        }
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                // 玻璃档 WebView 延伸到顶栏下方供模糊采样，仅底部保留导航栏 inset
                .padding(bottom = chrome.content.calculateBottomPadding())
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
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
                            text = stringResource(R.string.bangumi_login_loading),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.bangumi_login_failed),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            webView?.reload()
                        }) {
                            Text(stringResource(R.string.common_retry))
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
                                        // 回调缺少 code：多为用户在授权页取消/服务端 4xx，
                                        // 不记录完整 URL（含 code/state 等凭据）
                                        AppLogManager.w("BangumiLogin", "OAuth 回调缺少 code，登录未完成")
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    AppLogManager.i("BangumiLogin", "登录页加载完成: $url")
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // 只记主框架失败：子资源失败与登录无关，避免噪声
                                    if (request?.isForMainFrame == true) {
                                        AppLogManager.w(
                                            "BangumiLogin",
                                            "登录页加载失败: ${request.url} " +
                                                "code=${error?.errorCode} ${error?.description}"
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        AppLogManager.w(
                                            "BangumiLogin",
                                            "登录页 HTTP 错误: ${request.url} " +
                                                "status=${errorResponse?.statusCode}"
                                        )
                                    }
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
