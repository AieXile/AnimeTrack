package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.CardInfoField
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.CardInfoFields
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.ui.theme.LocalCardInfoFields
import com.aiexile.animetrack.util.formatAirDateShort
import com.aiexile.animetrack.util.isUnaired
import com.aiexile.animetrack.util.weekdayFromAirDate

/**
 * 卡片信息栏（标题下方一行）：左右两个槽位各按设置里的 [CardInfoField] 渲染。
 *
 * 普通卡片（AnimeCard）与多季堆叠卡（AnimeCardStack）共用同一套槽位布局，
 * 避免两处各写一份导致截断、留白规则漂移。
 */

/** 卡片信息栏单个槽位解析后的内容；null 表示该槽位无内容（选「不显示」或字段本身无值）。 */
private sealed interface CardInfoValue {
    /** 普通文本 */
    data class Plain(val text: String) : CardInfoValue
    /** 评分：星星以内联内容渲染，与数字共用文本基线 */
    data class Rating(val score: Float) : CardInfoValue
}

/**
 * 槽位字段在 [anime] 上是否有内容，判空口径与 [cardInfoValue] 保持一致。
 * 布局据此决定是否让另一侧占满整行、以及日期是否省略年份。
 */
private fun cardInfoFieldHasValue(field: CardInfoField, anime: Anime): Boolean = when (field) {
    CardInfoField.NONE -> false
    CardInfoField.PROGRESS -> true
    CardInfoField.RATING -> anime.rating != null
    CardInfoField.AIR_DATE -> formatAirDateShort(anime.airDate) != null
    CardInfoField.AIR_WEEKDAY -> {
        val weekday = anime.airWeekday?.takeIf { it in 1..7 }
            ?: weekdayFromAirDate(anime.airDate)
        weekday != null && shouldShowAirWeekday(anime)
    }
    CardInfoField.UNWATCHED -> anime.effectiveMaxEpisodes - anime.watchedEpisodes > 0
}

/**
 * 放送星期只对连载中/未放送的番剧有意义：
 * 完结番剧的「周几更新」已没有信息价值，一律不占卡片位置。
 * isFinished 已包含用户手动覆盖（airingStatusOverride）的结果。
 */
private fun shouldShowAirWeekday(anime: Anime): Boolean =
    !anime.isFinished || isUnaired(anime.airDate)

/**
 * 解析槽位字段在 [anime] 上的实际内容。字段无值返回 null，
 * 让布局据此决定另一侧是否需要占满整行（而不是统一按半宽裁切）。
 *
 * @param omitDateYear 两侧字段同时有内容时传 true：日期省略年份，
 *   否则两个字段并排会被卡片宽度截断。
 * @param progressText 进度文本覆盖：堆叠卡传入季数进度（如 "2/3"），
 *   普通卡片不传则显示集数进度。
 */
@Composable
private fun cardInfoValue(
    field: CardInfoField,
    anime: Anime,
    omitDateYear: Boolean = false,
    progressText: String? = null
): CardInfoValue? = when (field) {
    CardInfoField.NONE -> null
    CardInfoField.PROGRESS -> CardInfoValue.Plain(
        progressText ?: if (anime.totalEpisodes > 0) {
            "${anime.watchedEpisodes}/${anime.totalEpisodes}"
        } else if (anime.currentEpisodes > 0) {
            "${anime.watchedEpisodes}/${anime.currentEpisodes}"
        } else {
            "${anime.watchedEpisodes}"
        }
    )
    CardInfoField.RATING -> anime.rating?.let { CardInfoValue.Rating(it) }
    CardInfoField.AIR_DATE -> formatAirDateShort(anime.airDate, omitDateYear)?.let { CardInfoValue.Plain(it) }
    CardInfoField.AIR_WEEKDAY -> {
        // airWeekday 缺失时用首播日期推算，只有年月格式的番剧推不出星期（返回 null）
        val weekday = anime.airWeekday?.takeIf { it in 1..7 }
            ?: weekdayFromAirDate(anime.airDate)
        if (weekday != null && shouldShowAirWeekday(anime)) {
            weekdayLabel(weekday)?.let { CardInfoValue.Plain(it) }
        } else {
            null
        }
    }
    CardInfoField.UNWATCHED -> {
        // 只显示「还有几集没看」；追平或未知总集数时不显示
        val remaining = anime.effectiveMaxEpisodes - anime.watchedEpisodes
        if (remaining > 0) CardInfoValue.Plain("+$remaining") else null
    }
}

/**
 * 卡片信息栏双槽位行。
 *
 * @param progressText 进度字段的文本覆盖（堆叠卡显示季数进度）：默认渲染集数进度
 */
@Composable
internal fun CardInfoRow(
    anime: Anime,
    fields: CardInfoFields = LocalCardInfoFields.current,
    progressText: String? = null
) {
    // 先判断两侧是否有内容：无值的一侧不占位，另一侧可占满整行；
    // 两侧都有内容时日期省略年份，把宽度让给同行的另一个字段
    val bothSlotsFilled = cardInfoFieldHasValue(fields.left, anime) &&
        cardInfoFieldHasValue(fields.right, anime)
    val leftInfo = cardInfoValue(fields.left, anime, omitDateYear = bothSlotsFilled, progressText = progressText)
    val rightInfo = cardInfoValue(fields.right, anime, omitDateYear = bothSlotsFilled, progressText = progressText)
    when {
        // 两侧都有值时按内容自然宽度靠两端排布：总宽放得下就都完整显示，
        // 放不下时由右侧先省略（左侧字段优先，通常是日期这类不该截断的信息）
        leftInfo != null && rightInfo != null -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                CardInfoValueContent(leftInfo)
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                CardInfoValueContent(rightInfo)
            }
        }
        leftInfo != null -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            CardInfoValueContent(leftInfo)
        }
        rightInfo != null -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            CardInfoValueContent(rightInfo)
        }
    }
}

/**
 * 信息栏是否在显示季数进度文本（堆叠卡用）：进度槽被挪到任意一侧都算显示。
 * 两侧都没选进度（如左槽设为「不显示」）时季数改由封面角标呈现。
 */
internal fun cardInfoFieldsShowSeasonProgress(fields: CardInfoFields): Boolean =
    fields.left == CardInfoField.PROGRESS || fields.right == CardInfoField.PROGRESS

@Composable
private fun CardInfoValueContent(value: CardInfoValue) {
    when (value) {
        is CardInfoValue.Plain -> CardInfoText(value.text)
        is CardInfoValue.Rating -> RatingText(value.score)
    }
}

@Composable
private fun CardInfoText(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RatingText(rating: Float) {
    // 星星以内联内容嵌入文本，与数字共用同一文本基线，保证垂直对齐
    val ratingText = buildAnnotatedString {
        appendInlineContent("star", "★")
        // 窄空格（U+2009，约普通空格一半宽），星星与数字间距适中
        append(" " + String.format("%.1f", rating))
    }
    Text(
        text = ratingText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = LocalAnimeColors.current.starFilled,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        inlineContent = mapOf(
            "star" to InlineTextContent(
                Placeholder(
                    // 略高于数字大写高度，底部压在基线上保持底部对齐
                    width = 12.5.sp,
                    height = 12.5.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                )
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                    contentDescription = null,
                    tint = LocalAnimeColors.current.starFilled,
                    // 下移使星星相对数字上下对称超出（AboveBaseline 底部贴基线，超出量全在顶部会显偏高）
                    modifier = Modifier.fillMaxSize().offset(y = 2.dp)
                )
            }
        )
    )
}

@Composable
private fun weekdayLabel(weekday: Int?): String? = when (weekday) {
    1 -> stringResource(R.string.weekday_mon)
    2 -> stringResource(R.string.weekday_tue)
    3 -> stringResource(R.string.weekday_wed)
    4 -> stringResource(R.string.weekday_thu)
    5 -> stringResource(R.string.weekday_fri)
    6 -> stringResource(R.string.weekday_sat)
    7 -> stringResource(R.string.weekday_sun)
    else -> null
}
