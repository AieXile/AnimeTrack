# TMDB 数据源集成设计

## 概述

在现有 Bangumi 搜索基础上，新增 TMDB（The Movie Database）作为番剧搜索和数据源。搜索界面添加源选择 Chip（Bangumi / TMDB / 全部），详情页添加匹配胶囊按钮用于绑定缺失的源 ID。

## 需求

- 搜索番剧时支持选择数据源：Bangumi、TMDB、全部
- 选择"全部"时同时搜索两个源，结果分区显示，卡片标注来源
- TMDB 添加的番剧从 TMDB 补充详情，Bangumi 添加的从 Bangumi 补充
- 详情页提供匹配胶囊按钮，当 bangumiId 或 tmdbId 缺失时可手动绑定
- TMDB API Key：内置默认 Key 兜底 + 设置页可修改

## 架构

方案：统一 SearchResult 模型。Bangumi 和 TMDB 搜索结果都映射为 `SearchResult`，UI 层只处理一种类型。

### 数据流

**搜索流程**：
```
用户选择源 → 输入关键词 → 搜索
  → Bangumi: searchSubjects() → List<BangumiSubject> → 映射为 List<SearchResult>
  → TMDB: search/tv → List<TmdbTvShow> → 映射为 List<SearchResult>
  → 全部: 两个源并行搜索，结果合并分区显示
→ 用户点击结果 → 根据 source 调用对应详情 API
  → Bangumi: getSubjectDetail(id) → 填充表单
  → TMDB: tv/{id} → 填充表单
→ 保存 Anime（写入 bangumiId 或 tmdbId）
```

**匹配流程**：
```
详情页检测 bangumiId == null || tmdbId == null
→ 显示匹配胶囊按钮
→ 用户点击 → 弹出搜索框（自动填入当前标题）
→ 搜索缺失 ID 对应的源
→ 用户选择正确结果 → 写入 ID 到 Room 记录
```

## 数据模型

### SearchResult（统一搜索结果）

```kotlin
enum class SearchSource { BANGUMI, TMDB }

data class SearchResult(
    val source: SearchSource,
    val sourceId: Int,
    val title: String,
    val coverUrl: String?,
    val episodeCount: Int?,
    val airDate: String?,
    val rating: Float?,
    val summary: String?,
    val episodeCountText: String
)
```

### TmdbTvShow（搜索结果，无集数）

```kotlin
data class TmdbTvShow(
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Float?
)
```

注意：TMDB 搜索接口不返回 `number_of_episodes`，该字段仅在详情接口中提供。

### TmdbTvDetail（详情，有集数）

```kotlin
data class TmdbTvDetail(
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Float?,
    @SerializedName("number_of_episodes")
    val numberOfEpisodes: Int?,
    @SerializedName("number_of_seasons")
    val numberOfSeasons: Int?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("genres")
    val genres: List<TmdbGenre>?
)

data class TmdbGenre(
    val id: Int,
    @SerializedName("name")
    val name: String
)
```

### TmdbSearchResponse（搜索响应包裹）

```kotlin
data class TmdbSearchResponse(
    val page: Int,
    val results: List<TmdbTvShow>,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("total_results")
    val totalResults: Int
)
```

### 映射逻辑

- `BangumiSubject → SearchResult`：直接映射，`coverUrl` 取 `images.large`
- `TmdbTvShow → SearchResult`：`coverUrl` = `https://image.tmdb.org/t/p/w500${posterPath}`，`episodeCount = null`（搜索结果无集数），`episodeCountText` 显示首播日期或"未知"

### Anime 表变更

新增 `tmdbId: Int? = null` 字段，与 `bangumiId` 不互斥，可同时有值。需要 Room Migration（新增列 + 索引）。

## 搜索 UI

### 源选择 Chip

搜索框右侧添加三个 Chip：`Bangumi` / `TMDB` / `全部`，默认选中 `Bangumi`。

- 选中 `Bangumi`：只搜索 Bangumi
- 选中 `TMDB`：只搜索 TMDB
- 选中 `全部`：同时搜索两个源，结果分区显示（Bangumi 区 + TMDB 区），所有卡片右下角标注来源

### 搜索结果卡片

统一使用 `SearchResult` 渲染，卡片样式与现有一致。选择"全部"时，卡片右下角显示来源标识。

### 添加流程

1. 用户选择源 → 输入关键词 → 搜索
2. 点击搜索结果 → 根据 `source` 调用对应详情 API
3. 详情返回后填充添加表单
4. 保存时写入 `bangumiId` 或 `tmdbId`

## 详情页匹配胶囊按钮

### 显示条件

当 `bangumiId == null || tmdbId == null` 时，详情页 TopAppBar 右上角显示匹配胶囊按钮。

### 交互流程

1. 用户点击胶囊按钮 → 弹出底部 Sheet/Dialog
2. 自动填入当前番剧标题作为搜索关键词
3. 搜索缺失 ID 对应的源：
   - `bangumiId == null` → 搜索 Bangumi
   - `tmdbId == null` → 搜索 TMDB
   - 两个都 null → 两个都搜索，分区显示
4. 用户选择正确结果 → 将 ID 写入 Room 记录
5. 胶囊按钮消失或变为已匹配状态

### 胶囊按钮样式

小胶囊形状，显示"匹配"文字 + 链接图标，与现有 TopAppBar 按钮风格一致。

## TMDB API 认证与网络层

### TmdbApiService

```kotlin
interface TmdbApiService {
    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("tv/{series_id}")
    suspend fun getTvDetail(
        @Path("series_id") seriesId: Int,
        @Query("language") language: String = "zh-CN"
    ): TmdbTvDetail
}
```

### RetrofitClient 新增

- TMDB Base URL: `https://api.themoviedb.org/3/`
- OkHttpClient 添加 `Authorization: Bearer xxx` 拦截器
- API Key 读取 `SettingsRepository.currentTmdbApiKey`（内存缓存），避免 DataStore 死锁

### API Key 管理

- `SettingsRepository` 新增 `@Volatile var currentTmdbApiKey: String?` 内存缓存
- `setTmdbApiKey()` 时同步更新缓存和 DataStore
- 初始化时从 DataStore 读取并填充缓存
- 内置默认 Key 作为兜底
- OkHttp 拦截器直接读 `currentTmdbApiKey`，不走 DataStore Flow

### 图片加载

TMDB 封面 URL：`https://image.tmdb.org/t/p/w500${posterPath}`，Coil 可直接加载。

## 错误处理

- **TMDB API Key 无效**：搜索失败提示"TMDB API Key 无效，请在设置中配置"
- **网络错误**：与现有 Bangumi 搜索错误处理一致
- **搜索结果为空**：显示"未找到相关番剧"
- **匹配绑定错误**：提示"绑定失败"，不影响已有数据

## 文件变更

| 文件 | 变更 |
|------|------|
| `Anime.kt` | 新增 `tmdbId: Int? = null` |
| `AnimeDao.kt` | 新增 tmdbId 索引 + Migration |
| `SearchResult.kt`（新建） | 统一搜索结果模型 + `SearchSource` 枚举 |
| `TmdbApiService.kt`（新建） | TMDB API 接口 + 响应模型 |
| `RetrofitClient.kt` | 新增 TMDB Retrofit 实例 + Bearer Token 拦截器 |
| `SettingsRepository.kt` | 新增 `tmdbApiKey`/`currentTmdbApiKey` + 设置方法 |
| `AnimeRepository.kt` | 新增 `searchTmdb()`, `getTmdbTvDetail()` |
| `HomeUiState.kt` | `searchResults` 改为 `List<SearchResult>`，新增 `searchSource` |
| `HomeViewModel.kt` | 搜索逻辑支持多源，`selectSearchResult` 根据 source 分支 |
| `HomeScreen.kt` | 搜索框右侧源选择 Chip，搜索结果卡片适配 |
| `AnimeDetailViewModel.kt` | 新增匹配搜索逻辑 |
| `AnimeDetailScreen.kt` | 匹配胶囊按钮 + 匹配搜索弹窗 |
| `SettingsScreen.kt` | 新增 TMDB API Key 设置项 |
