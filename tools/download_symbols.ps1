# 批量下载 Material Symbols Rounded (wght500, grad0) 并转换为 VectorDrawable
# fill0 版本命名 sym_<name>.xml；导航选中态 fill1 版本命名 sym_fill_<name>.xml
$icons = @(
  'account_circle','add','arrow_back','arrow_drop_down','arrow_upward','bookmarks',
  'brightness_high','brightness_low','calendar_month','calendar_clock','campaign','check',
  'check_box','check_box_outline_blank','check_circle','close','closed_caption','cloud',
  'cloud_download','cloud_upload','code','collections_bookmark','delete','description',
  'edit','email','error','event_busy','fast_forward','favorite','feedback','file_download',
  'file_open','filter_list','folder','folder_open','forum','fullscreen','fullscreen_exit',
  'history','home','image','inbox','info','insert_drive_file','key','keyboard_arrow_down',
  'keyboard_arrow_right','link','list_arrow','lock','lock_reset','login','mail','identity_platform',
  'memory','more_vert','movie','music_note','navigation','notifications','palette','pause',
  'photo_camera','play_arrow','play_circle','qr_code_scanner','replay','remove','rocket_launch',
  'save','schedule','screen_rotation','search','send','settings','share','skip_next','speed',
  'star','star_shine','storage','sync','text_fields','tune','vertical_align_top','volume_down',
  'volume_off','volume_up','bottom_navigation','font_download'
)

# 需要生成 fill1 选中态的导航图标
$fillIcons = @('home','collections_bookmark','calendar_clock','schedule','settings')

$outDir = "F:\Projects\AnimeTrack\app\src\main\res\drawable"
$failed = @()

function Convert-Svg($svg, $outFile) {
  if ($svg -notmatch '<path d="([^"]+)"') { return $false }
  $d = $Matches[1]
  $xml = @"
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group
        android:translateY="960">
        <path
            android:fillColor="#FF000000"
            android:pathData="$d" />
    </group>
</vector>
"@
  [IO.File]::WriteAllText($outFile, $xml, (New-Object System.Text.UTF8Encoding($false)))
  return $true
}

foreach ($name in $icons) {
  $svg = curl.exe -s "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/$name/wght500/24px.svg"
  if (-not (Convert-Svg $svg "$outDir\sym_$name.xml")) { $failed += $name; Write-Output "FAIL: $name" }
}

foreach ($name in $fillIcons) {
  $svg = curl.exe -s "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/$name/wght500fill1/24px.svg"
  if (-not (Convert-Svg $svg "$outDir\sym_fill_$name.xml")) { $failed += "fill:$name"; Write-Output "FAIL: fill $name" }
}

Write-Output "---- done ----"
if ($failed.Count -eq 0) { Write-Output "ALL OK" } else { Write-Output "FAILED: $($failed -join ', ')" }
