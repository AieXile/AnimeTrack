package com.aiexile.animetrack.ui.player

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.player.WebSourceClassifier
import com.aiexile.animetrack.ui.components.chrome.AppTopBar
import com.aiexile.animetrack.ui.components.chrome.ChromeSources
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** 媒体嗅探兜底弹窗（供网页选集入口使用）：默认显示原生解析状态，需要交互时可显示辅助网页。 */
@Composable
fun WebMediaParseDialog(
    pageUrl: String,
    userAgent: String?,
    referer: String?,
    onDismiss: () -> Unit,
    onSourcesAvailable: (List<com.aiexile.animetrack.model.WebMediaSource>) -> Unit = {},
    onPlay: (url: String, title: String?, headers: Map<String, String>, userAgent: String?, mimeType: String?) -> Unit
) {
    var showWeb by remember(pageUrl) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        WebMediaSnifferContent(
            pageUrl = pageUrl,
            userAgent = userAgent,
            referer = referer,
            visible = true,
            showWeb = showWeb,
            onShow = {},
            onShowWebChange = { showWeb = it },
            onDismiss = onDismiss,
            onSourcesAvailable = onSourcesAvailable,
            onPlay = onPlay
        )
    }
}

/**
 * 播放详情页内静默嗅探层：后台使用隐藏 WebView 捕获直链并直接起播；
 * 仅在超时、发现多个候选资源或用户主动打开「网页辅助」时展开交互面板，复用同一 WebView 实例。
 */
@Composable
fun WebMediaSnifferOverlay(
    pageUrl: String,
    userAgent: String?,
    referer: String?,
    expanded: Boolean,
    showWeb: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onShowWebChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSourcesAvailable: (List<com.aiexile.animetrack.model.WebMediaSource>) -> Unit = {},
    onPlay: (url: String, title: String?, headers: Map<String, String>, userAgent: String?, mimeType: String?) -> Unit
) {
    BackHandler(enabled = expanded) { onDismiss() }
    WebMediaSnifferContent(
        pageUrl = pageUrl,
        userAgent = userAgent,
        referer = referer,
        visible = expanded,
        showWeb = showWeb,
        onShow = { onExpandChange(true) },
        onShowWebChange = onShowWebChange,
        onDismiss = onDismiss,
        onSourcesAvailable = onSourcesAvailable,
        onPlay = onPlay
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WebMediaSnifferContent(
    pageUrl: String,
    userAgent: String?,
    referer: String?,
    visible: Boolean,
    showWeb: Boolean,
    onShow: () -> Unit,
    onShowWebChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSourcesAvailable: (List<com.aiexile.animetrack.model.WebMediaSource>) -> Unit,
    onPlay: (url: String, title: String?, headers: Map<String, String>, userAgent: String?, mimeType: String?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val candidates = remember { mutableStateListOf<String>() }
    val seen = remember { Collections.synchronizedSet(mutableSetOf<String>()) }
    val headers = remember { Collections.synchronizedMap(mutableMapOf<String, Map<String, String>>()) }
    val contentTypes = remember { Collections.synchronizedMap(mutableMapOf<String, String>()) }
    val active = remember { AtomicBoolean(true) }
    val currentPlay by rememberUpdatedState(onPlay)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentShow by rememberUpdatedState(onShow)
    val currentSourcesAvailable by rememberUpdatedState(onSourcesAvailable)
    val declaredSources = remember { linkedMapOf<String, com.aiexile.animetrack.model.WebMediaSource>() }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(true) }

    fun report(url: String, type: String?) {
        if (!active.get() || url.length !in 8..4096 || url.toHttpUrlOrNull() == null ||
            !WebSourceClassifier.isCandidate(url, type)) return
        if (!type.isNullOrBlank()) contentTypes[url] = type
        mainHandler.post {
            if (active.get() && seen.add(url)) candidates.add(url)
        }
    }
    val bridge = remember {
        CaptureBridge({ url, type -> report(url, type) }, { url, label, type ->
            if (url.length <= 4096 && label.length in 1..100 &&
                url.toHttpUrlOrNull() != null && WebSourceClassifier.isCandidate(url, type)) {
                mainHandler.post {
                    if (active.get() && declaredSources.size < 32) {
                        declaredSources[url] = com.aiexile.animetrack.model.WebMediaSource(
                            url, label, type.takeIf { it.isNotBlank() }
                        )
                        currentSourcesAvailable(declaredSources.values.toList())
                    }
                }
                report(url, type)
            }
        })
    }

    fun play(url: String) {
        if (!active.compareAndSet(true, false)) return
        val mimeType = when (WebSourceClassifier.classify(url, contentTypes[url])) {
            WebSourceClassifier.Kind.HLS -> "application/x-mpegURL"
            WebSourceClassifier.Kind.DASH -> "application/dash+xml"
            else -> WebSourceClassifier.mimeType(url)
        }
        currentPlay(
            url, pageTitle, headers[url].orEmpty(),
            webViewRef.value?.settings?.userAgentString ?: userAgent, mimeType
        )
    }

    fun destroy() {
        active.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        val view = webViewRef.value ?: return
        webViewRef.value = null
        runCatching { view.removeJavascriptInterface(CAPTURE_INTERFACE) }
        runCatching { view.stopLoading() }
        runCatching { view.destroy() }
    }

    LaunchedEffect(showWeb) {
        if (showWeb) {
            automatic = false
            webViewRef.value?.onResume()
        }
    }
    LaunchedEffect(pageUrl) {
        delay(20_000)
        timedOut = true
        automatic = false
        if (!showWeb) webViewRef.value?.onPause()
        currentShow()
    }
    LaunchedEffect(candidates.size) {
        if (candidates.size > 1 && !visible) {
            automatic = false
            currentShow()
            return@LaunchedEffect
        }
        if (!automatic || showWeb || candidates.size != 1) return@LaunchedEffect
        delay(1500)
        if (automatic && !showWeb && candidates.size == 1) play(candidates.single())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                destroy()
                currentDismiss()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); destroy() }
    }

    // 辅助窗口单独使用标准材质，WebView 不进入模糊/玻璃采样图层。
    CompositionLocalProvider(LocalChromeStyle provides ChromeStyle.STANDARD) {
        Surface(
            modifier = if (visible) {
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
            } else {
                Modifier.size(1.dp).alpha(0f)
            },
            color = if (visible) MaterialTheme.colorScheme.background else Color.Transparent
        ) {
            Column(modifier = if (visible) Modifier.fillMaxSize() else Modifier.size(1.dp)) {
                if (visible) {
                    AppTopBar(
                        title = stringResource(R.string.web_episode_resolving),
                        onBack = onDismiss,
                        sources = ChromeSources(null, null),
                        style = ChromeStyle.STANDARD
                    )
                }
                Box(
                    modifier = if (visible) Modifier.fillMaxWidth().weight(1f)
                    else Modifier.size(1.dp)
                ) {
                    AndroidView(
                        modifier = if (visible && showWeb) Modifier.fillMaxSize()
                        else Modifier.size(1.dp).alpha(0f),
                        factory = { context ->
                            WebView(context).apply {
                                userAgent?.takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                webViewRef.value = this
                                addJavascriptInterface(bridge, CAPTURE_INTERFACE)
                                webChromeClient = object : WebChromeClient() {
                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (active.get()) pageTitle = title
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): WebResourceResponse? {
                                        val url = request.url.toString()
                                        if (active.get()) {
                                            val candidate = WebSourceClassifier.isCandidate(url)
                                            // JS 能按响应类型识别无扩展名清单；为这些请求保留头。
                                            if (candidate || headers.size < 512) {
                                                headers[url] = request.requestHeaders.toMap()
                                            }
                                            if (candidate) report(url, null)
                                        }
                                        return null
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean = request.url.scheme !in setOf("http", "https")

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        if (active.get()) view.evaluateJavascript(HOOK_SCRIPT, null)
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        if (!active.get()) return
                                        view.evaluateJavascript(HOOK_SCRIPT, null)
                                        view.evaluateJavascript(SCAN_SCRIPT) { result ->
                                            if (active.get()) collectDomCandidates(result, seen) { candidates.add(it) }
                                        }
                                        // 仅触发媒体请求，解析阶段保持静音。
                                        view.evaluateJavascript(
                                            "document.querySelectorAll('video').forEach(function(v){v.muted=true;var p=v.play();if(p)p.catch(function(){});});",
                                            null
                                        )
                                    }
                                }
                                loadUrl(pageUrl, referer?.let { mapOf("Referer" to it) }.orEmpty())
                            }
                        }
                    )
                    if (visible && !showWeb) Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!timedOut) CircularProgressIndicator()
                        Text(stringResource(if (timedOut) R.string.web_episode_timeout else R.string.web_episode_resolving))
                        OutlinedButton(onClick = { onShowWebChange(true) }) {
                            Text(stringResource(R.string.web_episode_assist))
                        }
                    }
                }
                if (visible) {
                    Text(
                        stringResource(R.string.web_playback_candidates, candidates.size),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (candidates.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(candidates, key = { it }) { url -> CandidateRow(url) { play(url) } }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.web_source_episodes)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(url: String, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.LINK),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = WebSourceClassifier.label(url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
            contentDescription = stringResource(R.string.player_play),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 页面内 <video>/<source> 资源；blob: 由 JS 生成，不能直接交给 ExoPlayer。 */
private const val SCAN_SCRIPT = """
(function () {
  var urls = [];
  document.querySelectorAll('video, source').forEach(function (el) {
    if (el.currentSrc) urls.push(el.currentSrc);
    if (el.src) urls.push(el.src);
  });
  return JSON.stringify(urls);
})()
"""

private fun collectDomCandidates(
    result: String?,
    seen: MutableSet<String>,
    onNew: (String) -> Unit
) {
    if (result.isNullOrBlank()) return
    val decoded = runCatching { JsonParser.parseString(result).asString }.getOrNull() ?: return
    val array = runCatching { JsonParser.parseString(decoded).asJsonArray }.getOrNull() ?: return
    array.forEach { element ->
        val url = runCatching { element.asString }.getOrNull() ?: return@forEach
        if (WebSourceClassifier.isCandidate(url) && seen.add(url)) onNew(url)
    }
}


private const val CAPTURE_INTERFACE = "AnimeTrackCapture"

/** 页面内仅允许上报资源地址与响应类型的最小 JS 接口。 */
private class CaptureBridge(
    private val onReport: (url: String, contentType: String) -> Unit,
    private val onSource: (url: String, label: String, contentType: String) -> Unit
) {
    @JavascriptInterface
    fun report(url: String, contentType: String) {
        runCatching { onReport(url, contentType) }
    }

    @JavascriptInterface
    fun source(url: String, label: String, contentType: String) {
        runCatching { onSource(url, label, contentType) }
    }
}

/**
 * 钩住 fetch / XMLHttpRequest 与资源时间线：
 * HLS.js 等播放器常用无扩展名地址请求清单，只有响应类型能识别。
 */
private const val HOOK_SCRIPT = """
(function () {
  if (window.__animetrackHooked) {
    try { window.__animetrackReportResources && window.__animetrackReportResources(); } catch (e) {}
    return;
  }
  window.__animetrackHooked = true;
  function report(url, ct) {
    try {
      if (window.AnimeTrackCapture && url) {
        var absolute = String(url);
        try { absolute = new URL(absolute, location.href).href; } catch (e) {}
        window.AnimeTrackCapture.report(absolute, String(ct || ''));
      }
    } catch (e) {}
  }
  var originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url);
      var promise = originalFetch.apply(this, arguments);
      if (url) {
        promise.then(function (response) {
          try { report(url, response.headers.get('content-type')); } catch (e) {}
        }).catch(function () {});
      }
      return promise;
    };
  }
  var originalOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__animetrackUrl = url;
    return originalOpen.apply(this, arguments);
  };
  var originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    if (xhr.__animetrackUrl) {
      xhr.addEventListener('load', function () {
        var type = '';
        try { type = xhr.getResponseHeader('content-type') || ''; } catch (e) {}
        report(xhr.__animetrackUrl, type);
      });
    }
    return originalSend.apply(this, arguments);
  };
  window.__animetrackReportResources = function () {
    try {
      performance.getEntriesByType('resource').forEach(function (entry) {
        if (entry.initiatorType === 'xmlhttprequest' ||
            entry.initiatorType === 'fetch' ||
            entry.initiatorType === 'video') {
          report(entry.name, '');
        }
      });
      var video = document.querySelector('video');
      if (video) report(video.currentSrc || video.src || '', '');
      function quality(source) {
        if (!source || typeof source !== 'object') return;
        var url = source.src || source.url || source.file;
        var label = source.label || source.name || source.quality || source.res || source.size;
        if (!url || !label) return;
        try {
          window.AnimeTrackCapture.source(new URL(String(url), location.href).href,
            String(label), String(source.type || ''));
        } catch (e) {}
      }
      document.querySelectorAll('video source[src], video[src]').forEach(function (el) {
        quality({src: el.src, label: el.getAttribute('label') || el.getAttribute('data-quality') ||
          el.getAttribute('data-res') || el.getAttribute('res') || el.getAttribute('size'), type: el.type});
      });
      var configurations = [window.player_aaaa, window.playerConfig, window.playerOptions];
      configurations.forEach(function (config) {
        if (!config || typeof config !== 'object') return;
        [config.sources, config.quality, config.qualities].forEach(function (items) {
          if (Array.isArray(items)) items.slice(0, 32).forEach(quality);
        });
      });
    } catch (e) {}
  };
  setInterval(window.__animetrackReportResources, 2000);
})()
"""
