package com.aiexile.animetrack.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.aiexile.animetrack.R

/**
 * 应用统一图标枚举：每个条目绑定 Material Symbols 与 Lucide 两套 drawable。
 *
 * UI 层不直接引用 R.drawable.sym_* / R.drawable.lucide_*，
 * 统一经 [AppIcon] / [rememberAppIconPainter] 渲染，以支持外观设置中切换图标包。
 * 条目名与 sym 资源名一一对应（FILLED 后缀 = 导航选中态填充变体，Lucide 包下复用基础图标）。
 */
enum class AppIcon(
    @param:DrawableRes val materialRes: Int,
    @param:DrawableRes val lucideRes: Int
) {
    ACCOUNT_CIRCLE(R.drawable.sym_account_circle, R.drawable.lucide_circle_user),
    ADD(R.drawable.sym_add, R.drawable.lucide_plus),
    ARROW_BACK(R.drawable.sym_arrow_back, R.drawable.lucide_arrow_left),
    ARROW_DROP_DOWN(R.drawable.sym_arrow_drop_down, R.drawable.lucide_chevron_down),
    ARROW_UPWARD(R.drawable.sym_arrow_upward, R.drawable.lucide_arrow_up),
    BOOKMARKS(R.drawable.sym_bookmarks, R.drawable.lucide_bookmark),
    BOTTOM_NAVIGATION(R.drawable.sym_bottom_navigation, R.drawable.lucide_panel_bottom),
    BRIGHTNESS_HIGH(R.drawable.sym_brightness_high, R.drawable.lucide_sun),
    BRIGHTNESS_LOW(R.drawable.sym_brightness_low, R.drawable.lucide_sun_dim),
    CALENDAR_CLOCK(R.drawable.sym_calendar_clock, R.drawable.lucide_calendar_clock),
    CALENDAR_MONTH(R.drawable.sym_calendar_month, R.drawable.lucide_calendar),
    CALENDAR_VIEW_DAY(R.drawable.sym_calendar_view_day, R.drawable.lucide_timeline),
    /** 追番看板右上角“今明日预览”按钮：Lucide 用两天日历（calendar-days） */
    CALENDAR_DAYS(R.drawable.sym_calendar_month, R.drawable.lucide_calendar_days),
    CAMPAIGN(R.drawable.sym_campaign, R.drawable.lucide_megaphone),
    CHECK(R.drawable.sym_check, R.drawable.lucide_check),
    CHECK_BOX(R.drawable.sym_check_box, R.drawable.lucide_square_check),
    CHECK_BOX_OUTLINE_BLANK(R.drawable.sym_check_box_outline_blank, R.drawable.lucide_square),
    CHECK_CIRCLE(R.drawable.sym_check_circle, R.drawable.lucide_circle_check),
    CLOSE(R.drawable.sym_close, R.drawable.lucide_x),
    CLOSED_CAPTION(R.drawable.sym_closed_caption, R.drawable.lucide_captions),
    CLOUD(R.drawable.sym_cloud, R.drawable.lucide_cloud),
    CLOUD_DOWNLOAD(R.drawable.sym_cloud_download, R.drawable.lucide_cloud_download),
    CLOUD_UPLOAD(R.drawable.sym_cloud_upload, R.drawable.lucide_cloud_upload),
    CODE(R.drawable.sym_code, R.drawable.lucide_code),
    COLLECTIONS_BOOKMARK(R.drawable.sym_collections_bookmark, R.drawable.lucide_library_big),
    DELETE(R.drawable.sym_delete, R.drawable.lucide_trash_2),
    DESCRIPTION(R.drawable.sym_description, R.drawable.lucide_file_text),
    EDIT(R.drawable.sym_edit, R.drawable.lucide_pencil),
    EMAIL(R.drawable.sym_email, R.drawable.lucide_mail),
    ERROR(R.drawable.sym_error, R.drawable.lucide_circle_alert),
    EVENT_BUSY(R.drawable.sym_event_busy, R.drawable.lucide_calendar_x),
    FAST_FORWARD(R.drawable.sym_fast_forward, R.drawable.lucide_fast_forward),
    FAVORITE(R.drawable.sym_favorite, R.drawable.lucide_heart),
    FEEDBACK(R.drawable.sym_feedback, R.drawable.lucide_message_square_warning),
    FILE_DOWNLOAD(R.drawable.sym_file_download, R.drawable.lucide_download),
    FILE_OPEN(R.drawable.sym_file_open, R.drawable.lucide_file_up),
    FOLDER(R.drawable.sym_folder, R.drawable.lucide_folder),
    FOLDER_OPEN(R.drawable.sym_folder_open, R.drawable.lucide_folder_open),
    FONT_DOWNLOAD(R.drawable.sym_font_download, R.drawable.lucide_type),
    FORUM(R.drawable.sym_forum, R.drawable.lucide_messages_square),
    FULLSCREEN(R.drawable.sym_fullscreen, R.drawable.lucide_maximize),
    FULLSCREEN_EXIT(R.drawable.sym_fullscreen_exit, R.drawable.lucide_minimize),
    HISTORY(R.drawable.sym_history, R.drawable.lucide_history),
    HOME(R.drawable.sym_home, R.drawable.lucide_house),
    IDENTITY_PLATFORM(R.drawable.sym_identity_platform, R.drawable.lucide_scan_face),
    IMAGE(R.drawable.sym_image, R.drawable.lucide_image),
    INBOX(R.drawable.sym_inbox, R.drawable.lucide_inbox),
    INFO(R.drawable.sym_info, R.drawable.lucide_info),
    INSERT_DRIVE_FILE(R.drawable.sym_insert_drive_file, R.drawable.lucide_file),
    KEY(R.drawable.sym_key, R.drawable.lucide_key_round),
    KEYBOARD_ARROW_DOWN(R.drawable.sym_keyboard_arrow_down, R.drawable.lucide_chevron_down),
    KEYBOARD_ARROW_RIGHT(R.drawable.sym_keyboard_arrow_right, R.drawable.lucide_chevron_right),
    LINK(R.drawable.sym_link, R.drawable.lucide_link),
    LIST_ARROW(R.drawable.sym_list_arrow, R.drawable.lucide_list_chevrons_up_down),
    LOCK(R.drawable.sym_lock, R.drawable.lucide_lock),
    LOCK_RESET(R.drawable.sym_lock_reset, R.drawable.lucide_key_round),
    MAIL(R.drawable.sym_mail, R.drawable.lucide_mail),
    MEMORY(R.drawable.sym_memory, R.drawable.lucide_cpu),
    MORE_VERT(R.drawable.sym_more_vert, R.drawable.lucide_ellipsis_vertical),
    MOVIE(R.drawable.sym_movie, R.drawable.lucide_clapperboard),
    MUSIC_NOTE(R.drawable.sym_music_note, R.drawable.lucide_music),
    NOTIFICATIONS(R.drawable.sym_notifications, R.drawable.lucide_bell),
    PALETTE(R.drawable.sym_palette, R.drawable.lucide_palette),
    PAUSE(R.drawable.sym_pause, R.drawable.lucide_pause),
    PHOTO_CAMERA(R.drawable.sym_photo_camera, R.drawable.lucide_camera),
    PLAY_ARROW(R.drawable.sym_play_arrow, R.drawable.lucide_play),
    PLAY_CIRCLE(R.drawable.sym_play_circle, R.drawable.lucide_circle_play),
    QR_CODE_SCANNER(R.drawable.sym_qr_code_scanner, R.drawable.lucide_qr_code),
    REMOVE(R.drawable.sym_remove, R.drawable.lucide_minus),
    REPLAY(R.drawable.sym_replay, R.drawable.lucide_rotate_ccw),
    ROCKET_LAUNCH(R.drawable.sym_rocket_launch, R.drawable.lucide_rocket),
    SAVE(R.drawable.sym_save, R.drawable.lucide_save),
    SCREEN_ROTATION(R.drawable.sym_screen_rotation, R.drawable.lucide_rotate_cw),
    SEARCH(R.drawable.sym_search, R.drawable.lucide_search),
    SEND(R.drawable.sym_send, R.drawable.lucide_send),
    SETTINGS(R.drawable.sym_settings, R.drawable.lucide_settings),
    SHARE(R.drawable.sym_share, R.drawable.lucide_share_2),
    SKIP_NEXT(R.drawable.sym_skip_next, R.drawable.lucide_skip_forward),
    SPEED(R.drawable.sym_speed, R.drawable.lucide_gauge),
    STAR_SHINE(R.drawable.sym_star_shine, R.drawable.lucide_star_fill),
    STORAGE(R.drawable.sym_storage, R.drawable.lucide_hard_drive),
    SYNC(R.drawable.sym_sync, R.drawable.lucide_refresh_cw),
    TUNE(R.drawable.sym_tune, R.drawable.lucide_sliders_horizontal),
    VERTICAL_ALIGN_TOP(R.drawable.sym_vertical_align_top, R.drawable.lucide_arrow_up_to_line),
    VOLUME_DOWN(R.drawable.sym_volume_down, R.drawable.lucide_volume_1),
    VOLUME_OFF(R.drawable.sym_volume_off, R.drawable.lucide_volume_x),
    VOLUME_UP(R.drawable.sym_volume_up, R.drawable.lucide_volume_2),
    // 导航选中态填充变体：Lucide 包下复用基础图标（颜色区分选中态）
    HOME_FILLED(R.drawable.sym_fill_home, R.drawable.lucide_house),
    COLLECTIONS_BOOKMARK_FILLED(R.drawable.sym_fill_collections_bookmark, R.drawable.lucide_library_big),
    CALENDAR_VIEW_DAY_FILLED(R.drawable.sym_fill_calendar_view_day, R.drawable.lucide_timeline),
    CALENDAR_CLOCK_FILLED(R.drawable.sym_fill_calendar_clock, R.drawable.lucide_calendar_clock),
    SETTINGS_FILLED(R.drawable.sym_fill_settings, R.drawable.lucide_settings)
}

/**
 * 图标渲染组件：签名对齐 material3.Icon，按 [LocalIconPack] 解析当前包的 drawable。
 */
@Composable
fun AppIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(LocalIconPack.current.resolve(icon)),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/**
 * 获取当前图标包下的 Painter：
 * 用于 InlineTextContent、持有 Painter 参数的组件（SettingCard 等）等不便直接换用 [AppIcon] 的场景。
 */
@Composable
fun rememberAppIconPainter(icon: AppIcon): Painter =
    painterResource(LocalIconPack.current.resolve(icon))
