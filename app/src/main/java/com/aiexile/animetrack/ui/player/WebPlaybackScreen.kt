package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.player.PendingWebPlayback
import com.aiexile.animetrack.data.player.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.WebSourceClassifier
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 网页播放入口：直链播放，网页先进入原生选集页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPlaybackScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: WebPlaybackViewModel = viewModel(factory = WebPlaybackViewModel.Factory()),
    sourceViewModel: WebSourceViewModel = viewModel(factory = WebSourceViewModel.Factory()),
    pageViewModel: WebPageViewModel = viewModel(factory = WebPageViewModel.Factory())
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val sourceRules by sourceViewModel.rules.collectAsStateWithLifecycle()
    val selectedSourceRule by sourceViewModel.selectedRule.collectAsStateWithLifecycle()
    val sourceSearchState by sourceViewModel.searchState.collectAsStateWithLifecycle()
    val sourceEpisodeState by sourceViewModel.episodeState.collectAsStateWithLifecycle()
    val sourceImportState by sourceViewModel.importState.collectAsStateWithLifecycle()
    val pageState by pageViewModel.state.collectAsStateWithLifecycle()
    val mediaState by pageViewModel.mediaState.collectAsStateWithLifecycle()
    var urlInput by rememberSaveable { mutableStateOf("") }
    var parseUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var parseUserAgent by rememberSaveable { mutableStateOf<String?>(null) }
    var parseReferer by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEpisodeUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceKeyword by rememberSaveable { mutableStateOf("") }
    var showSourceSearch by rememberSaveable { mutableStateOf(false) }
    var showSourceManage by rememberSaveable { mutableStateOf(false) }

    fun parsePage(url: String, userAgent: String? = null, referer: String? = null) {
        parseUserAgent = userAgent
        parseReferer = referer
        parseUrl = url
        selectedEpisodeUrl = null
        pageViewModel.load(url, userAgent, referer, force = true)
    }

    fun openPlayer(
        target: String,
        pageUrl: String?,
        title: String?,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        referer: String? = null,
        mimeType: String? = null,
        resolvePage: Boolean = false,
        queue: com.aiexile.animetrack.domain.media.WebPlaybackQueue? = null
    ) {
        val playbackQueue = queue ?: (pageState as? WebPageState.Success)?.page?.let {
            com.aiexile.animetrack.domain.media.WebPlaybackQueue.from(it, pageUrl ?: target)
        }
        PendingWebPlayback.set(
            PendingWebPlaybackRequest(
                url = target,
                mimeType = mimeType ?: WebSourceClassifier.mimeType(target),
                title = title,
                pageUrl = pageUrl,
                headers = headers,
                userAgent = userAgent,
                referer = referer,
                resolvePage = resolvePage,
                queue = playbackQueue
            )
        )
        viewModel.remember(pageUrl ?: target, title, userAgent, referer, playbackQueue?.seriesUrl, playbackQueue)
        pageViewModel.cancelMedia()
        if (pageUrl == null) {
            parseUrl = null
            pageViewModel.close()
        }
        onOpenPlayer()
    }

    LaunchedEffect(parseUrl) {
        parseUrl?.let { pageViewModel.load(it, parseUserAgent, parseReferer) }
    }

    AppScaffold(
        title = stringResource(R.string.web_playback_title),
        onBack = onBack
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(chrome.content)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AppOutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.web_playback_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val url = urlInput.trim()
                        if (url.toHttpUrlOrNull() != null) openPlayer(url, null, url)
                    },
                    enabled = urlInput.trim().toHttpUrlOrNull() != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.web_playback_play_direct))
                }
                OutlinedButton(
                    onClick = {
                        val url = urlInput.trim()
                        if (url.toHttpUrlOrNull() != null) parsePage(url)
                    },
                    enabled = urlInput.trim().toHttpUrlOrNull() != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.web_playback_parse_page))
                }
            }

            Text(
                text = stringResource(R.string.web_playback_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            WebSourceSection(
                rules = sourceRules,
                selectedRule = selectedSourceRule,
                keyword = sourceKeyword,
                onKeywordChange = { sourceKeyword = it },
                onSelectRule = sourceViewModel::selectRule,
                onSearch = {
                    val rule = selectedSourceRule
                    if (rule != null && sourceKeyword.isNotBlank()) {
                        sourceViewModel.search(rule, sourceKeyword)
                        showSourceSearch = true
                    }
                },
                onManage = {
                    sourceViewModel.clearImportState()
                    showSourceManage = true
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.web_playback_history),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (history.isNotEmpty()) {
                    OutlinedButton(onClick = viewModel::clear) {
                        Text(stringResource(R.string.web_playback_clear_history))
                    }
                }
            }
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.web_playback_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            history.forEach { item ->
                WebPlaybackHistoryCard(
                    item = item,
                    onContinue = {
                        viewModel.prepareContinue(item)
                        onOpenPlayer()
                    },
                    onEpisodes = {
                        urlInput = item.entry.url
                        parsePage(item.entry.url, item.entry.userAgent, item.entry.referer)
                        selectedEpisodeUrl = item.entry.url
                    },
                    onDelete = { viewModel.remove(item.entry.url) }
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (!showSourceSearch) parseUrl?.let { entryUrl ->
        val page = (pageState as? WebPageState.Success)?.page
        val seriesUrl = page?.channels?.indices?.firstOrNull()?.let {
            com.aiexile.animetrack.domain.media.WebPlaybackQueue(page, it, 0).seriesUrl
        }
        val latest = history.firstOrNull { item ->
            item.entry.seriesUrl == seriesUrl && seriesUrl != null
        }
        WebEpisodeDialog(
            state = pageState,
            selectedUrl = latest?.entry?.url ?: selectedEpisodeUrl,
            progress = progress,
            onDismiss = { parseUrl = null; pageViewModel.close() },
            onRetry = { pageViewModel.load(entryUrl, parseUserAgent, parseReferer, force = true) },
            onAssist = { pageViewModel.assist(entryUrl) },
            onSelect = { episode ->
                val context = pageViewModel.request
                selectedEpisodeUrl = episode.url
                val page = (pageState as? WebPageState.Success)?.page
                val title = page?.title
                openPlayer(episode.url, episode.url,
                    listOfNotNull(title, episode.name).joinToString(" · "),
                    userAgent = context?.userAgent, referer = context?.referer ?: episode.url,
                    resolvePage = true,
                    queue = page?.let { com.aiexile.animetrack.domain.media.WebPlaybackQueue.from(it, episode.url) })
            }
        )
        (mediaState as? WebMediaState.Sniffing)?.let { sniffing ->
            androidx.compose.runtime.key(sniffing.episode.url) {
                WebMediaParseDialog(
                    pageUrl = sniffing.episode.url,
                    userAgent = pageViewModel.request?.userAgent,
                    referer = pageViewModel.request?.referer,
                    onDismiss = pageViewModel::cancelMedia,
                    onPlay = { target, title, headers, actualUserAgent, mimeType ->
                        val pageTitle = (pageState as? WebPageState.Success)?.page?.title
                        openPlayer(target, sniffing.episode.url,
                            pageTitle?.let { "$it · ${sniffing.episode.name}" } ?: title,
                            headers, actualUserAgent, pageViewModel.request?.referer, mimeType)
                    }
                )
            }
        }
    }

    val activeSourceRule = (sourceSearchState as? WebSourceSearchState.Success)?.rule
        ?: selectedSourceRule

    if (showSourceSearch) {
        WebSourceSearchDialog(
            ruleName = activeSourceRule?.name.orEmpty(),
            searchState = sourceSearchState,
            episodeState = sourceEpisodeState,
            onDismiss = {
                showSourceSearch = false
                sourceViewModel.reset()
            },
            onRetry = {
                when {
                    sourceEpisodeState is WebSourceEpisodeState.Failure ->
                        sourceViewModel.retryEpisodes()

                    sourceSearchState is WebSourceSearchState.Failure ->
                        sourceViewModel.retrySearch()
                }
            },
            onBackToResults = sourceViewModel::resetEpisodes,
            onSelectEntry = { entry ->
                activeSourceRule?.let { rule ->
                    if (rule.isApiChapter) {
                        // API 选集的 source 通常是站点 ID，没有可打开的详情页；
                        // 留在弹层里取剧集，选中后再进 WebView 嗅探播放页。
                        sourceViewModel.openEntry(rule, entry)
                    } else {
                        showSourceSearch = false
                        parsePage(entry.url, rule.effectiveUserAgent, rule.effectiveReferer)
                    }
                }
            },
            onSelectEpisode = { episode ->
                val result = sourceEpisodeState as? WebSourceEpisodeState.Success
                val rule = result?.rule
                if (rule != null) {
                    showSourceSearch = false
                    val page = com.aiexile.animetrack.model.WebSourcePage(
                        result.entry.name, result.entry.url,
                        result.episodes.groupBy { it.channelIndex }.values.map {
                            com.aiexile.animetrack.model.WebSourceChannel(null, it)
                        }, detailUrl = result.entry.url)
                    openPlayer(episode.url, episode.url, episode.name,
                        userAgent = rule.effectiveUserAgent, referer = rule.effectiveReferer,
                        resolvePage = true,
                        queue = com.aiexile.animetrack.domain.media.WebPlaybackQueue.from(page, episode.url))
                }
            }
        )
    }

    if (showSourceManage) {
        WebSourceManageDialog(
            rules = sourceRules,
            importState = sourceImportState,
            onImportText = sourceViewModel::importText,
            onImportUrl = sourceViewModel::importFromUrl,
            onDelete = sourceViewModel::deleteRule,
            onDismiss = {
                showSourceManage = false
                sourceViewModel.clearImportState()
            }
        )
    }
}

