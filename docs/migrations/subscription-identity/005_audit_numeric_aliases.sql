-- 订阅身份 v2：数字 alias 只读审计（P0-1）
-- 在 animetrack 库执行；本文件只包含 SELECT，不修改任何数据。
--
-- 背景：
-- 阶段 2 回填把「纯数字 anime_id」一律当成 Bangumi ID 写进了 bangumi_id + alias(type='bangumi')。
-- 旧客户端的本地 Room 自增 id 也是纯数字，因此存量 alias 中可能混入了并非 Bangumi 的本地 id。
-- 当前代码不做自动重分类（无法区分「已验证的 Bangumi 数字」与「未验证的本地数字」，
-- 自动改名/重分类会误伤合法改名的番剧）。先跑本审计，人工确认样本后，再决定是否
-- 需要一次性数据修复 migration。
--
-- 判读建议：
-- 1. 同一数字值被多个用户引用、且标题差异很大 → 高度疑似本地自增 id 污染；
-- 2. 同一数字值被多个用户引用但标题只是翻译/简繁差异 → 可能是合法 Bangumi ID，
--    需要结合 Bangumi 条目接口人工核对；
-- 3. 单个用户、单条记录的小数字无法自动判定，不要仅凭值域直接删除 alias。

-- ============================================================
-- Q1. 跨用户数字 alias 标题分叉（最强污染信号）
-- ============================================================
SELECT
  a.alias_value                                         AS numeric_alias,
  COUNT(DISTINCT a.user_id)                             AS user_count,
  COUNT(DISTINCT s.anime_title)                         AS title_count,
  GROUP_CONCAT(DISTINCT LEFT(s.anime_title, 48)
    ORDER BY s.anime_title SEPARATOR ' | ')             AS sample_titles
FROM subscription_aliases a
JOIN user_subscriptions s
  ON s.user_id = a.user_id
 AND s.subscription_id = a.subscription_id
WHERE a.alias_type = 'bangumi'
  AND a.alias_value REGEXP '^[1-9][0-9]*$'
GROUP BY a.alias_value
HAVING user_count > 1 AND title_count > 1
ORDER BY title_count DESC, user_count DESC
LIMIT 200;

-- ============================================================
-- Q2. 小数字 alias（本地自增 id 典型值域，需人工复核）
-- 说明：Bangumi 早期条目也有小 ID，值域只能用来排序，不能直接判死。
-- ============================================================
SELECT
  a.alias_value                     AS numeric_alias,
  COUNT(*)                          AS alias_rows,
  COUNT(DISTINCT a.user_id)         AS user_count,
  COUNT(DISTINCT s.anime_title)     AS title_count,
  GROUP_CONCAT(DISTINCT LEFT(s.anime_title, 48)
    ORDER BY s.anime_title SEPARATOR ' | ') AS sample_titles
FROM subscription_aliases a
JOIN user_subscriptions s
  ON s.user_id = a.user_id
 AND s.subscription_id = a.subscription_id
WHERE a.alias_type = 'bangumi'
  AND a.alias_value REGEXP '^[1-9][0-9]*$'
  AND CAST(a.alias_value AS UNSIGNED) <= 10000
GROUP BY a.alias_value
ORDER BY alias_rows DESC, title_count DESC, numeric_alias ASC
LIMIT 300;

-- ============================================================
-- Q3. 数字 alias 命中但标题不一致的写入痕迹（合并日志）
-- 用于核对诊断日志里反复出现的 key / subscriptionId 是否真的被合并过。
-- ============================================================
SELECT
  l.id,
  l.user_id,
  l.target_subscription_id,
  l.merged_subscription_id,
  l.reason,
  JSON_UNQUOTE(JSON_EXTRACT(l.source_row, '$.anime_id')) AS source_anime_id,
  JSON_UNQUOTE(JSON_EXTRACT(l.source_row, '$.anime_title')) AS source_title,
  JSON_UNQUOTE(JSON_EXTRACT(l.target_row, '$.anime_id')) AS target_anime_id,
  JSON_UNQUOTE(JSON_EXTRACT(l.target_row, '$.anime_title')) AS target_title,
  l.created_at
FROM subscription_merge_log l
WHERE l.reason IN ('alias_conflict', 'bind_projection', 'upsert_multi_hit', 'legacy_title')
ORDER BY l.created_at DESC
LIMIT 200;

-- ============================================================
-- Q4. 墓碑里的数字别名（被删除/恢复循环用过的键）
-- 与 Q1 交叉核对：墓碑数字键如果也出现在跨用户标题分叉里，更能确认污染。
-- ============================================================
SELECT
  t.alias_value                    AS numeric_alias,
  COUNT(DISTINCT t.user_id)        AS user_count,
  COUNT(*)                         AS tombstone_rows
FROM subscription_tombstone_aliases t
WHERE t.alias_value REGEXP '^[1-9][0-9]*$'
GROUP BY t.alias_value
ORDER BY user_count DESC, tombstone_rows DESC
LIMIT 200;
