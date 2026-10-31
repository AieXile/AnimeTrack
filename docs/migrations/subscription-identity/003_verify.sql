-- 订阅身份 v2：阶段 2 校验
-- 在 animetrack 库执行。
-- 通过标准：第 1–4、6–7 条查询返回 0 行；第 5 条返回计数 0（它本身就会输出一行 0）。

-- 1. 每个订阅必须恰好一行
SELECT subscription_id, COUNT(*) AS c
FROM user_subscriptions
WHERE subscription_id IS NOT NULL
GROUP BY subscription_id
HAVING c <> 1;

-- 2. 每个 alias 必须指向存在的订阅
SELECT a.user_id, a.alias_value, a.subscription_id
FROM subscription_aliases a
LEFT JOIN user_subscriptions s
  ON s.user_id = a.user_id AND s.subscription_id = a.subscription_id
WHERE s.subscription_id IS NULL;

-- 3. 同一用户同一 bangumi 不得多行
SELECT user_id, bangumi_id, COUNT(*) AS c
FROM user_subscriptions
WHERE bangumi_id IS NOT NULL
GROUP BY user_id, bangumi_id
HAVING c > 1;

-- 4. 每个 canonical 的 anime_id 投影必须正确
SELECT user_id, subscription_id, anime_id, bangumi_id, remote_sync_id
FROM user_subscriptions
WHERE anime_id <> COALESCE(CAST(bangumi_id AS CHAR), remote_sync_id, anime_id);

-- 5. 不允许空 subscription_id（回填后应为 0）
SELECT COUNT(*) AS missing_subscription_id
FROM user_subscriptions
WHERE subscription_id IS NULL;

-- 6. 同一用户同一 remote_sync_id 不得多行
SELECT user_id, remote_sync_id, COUNT(*) AS c
FROM user_subscriptions
WHERE remote_sync_id IS NOT NULL
GROUP BY user_id, remote_sync_id
HAVING c > 1;

-- 7. 墓碑别名不得指向已存在的订阅（存在说明清理逻辑有遗漏，通常是可恢复的正常状态，
--    但如果该订阅 id 同时还有 user_subscriptions 行，需要人工确认）
SELECT t.user_id, t.subscription_id
FROM subscription_tombstones t
JOIN user_subscriptions s
  ON s.user_id = t.user_id AND s.subscription_id = t.subscription_id;
