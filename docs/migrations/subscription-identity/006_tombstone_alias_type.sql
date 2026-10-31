-- 订阅身份 v2：墓碑别名补 alias_type
--
-- 背景：
-- subscription_tombstone_aliases 最初只存 alias_value，客户端无法区分
-- 「Bangumi ID / 客户端 UUID / 旧本地自增 id」。App 消费 tombstones 时如果
-- 把所有字符串都当稳定键，会把另一台设备的本地 12 误当成 Bangumi 12，
-- 删掉错误的本地卡片。
--
-- 本文件可重复执行（先查 information_schema，再决定是否 ALTER）。
-- 生产建议先备份：
--   CREATE TABLE subscription_tombstone_aliases_bak_20261029
--     AS SELECT * FROM subscription_tombstone_aliases;
--
-- 新版 server.js 启动时 subscriptions/schema.js 也会幂等执行同样的逻辑。

SET @has_alias_type := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'subscription_tombstone_aliases'
    AND COLUMN_NAME = 'alias_type'
);

SET @ddl := IF(
  @has_alias_type = 0,
  'ALTER TABLE subscription_tombstone_aliases
     ADD COLUMN alias_type ENUM(''bangumi'',''remote_sync'',''legacy_local'')
       NULL COMMENT ''键语义，历史行为 NULL'' AFTER alias_value',
  'SELECT ''alias_type already exists'' AS notice'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史行按删除快照推断：
-- 与 snapshot.bangumi_id 相同 → bangumi
-- 与 snapshot.remote_sync_id 相同 → remote_sync
-- 其余 → legacy_local（运行时仍只做标题二次确认）
UPDATE subscription_tombstone_aliases ta
JOIN subscription_tombstones t
  ON t.user_id = ta.user_id AND t.subscription_id = ta.subscription_id
SET ta.alias_type = CASE
  WHEN JSON_UNQUOTE(JSON_EXTRACT(t.row_snapshot, '$.bangumi_id')) = ta.alias_value
    THEN 'bangumi'
  WHEN JSON_UNQUOTE(JSON_EXTRACT(t.row_snapshot, '$.remote_sync_id')) = ta.alias_value
    THEN 'remote_sync'
  ELSE 'legacy_local'
END
WHERE ta.alias_type IS NULL;

-- 校验：不应再存在 NULL
SELECT COUNT(*) AS null_alias_type_rows
FROM subscription_tombstone_aliases
WHERE alias_type IS NULL;

-- 人工抽查：仍被标成 legacy_local 的数字键（这些只用于标题二次确认）
SELECT user_id, alias_value, subscription_id
FROM subscription_tombstone_aliases
WHERE alias_type = 'legacy_local'
  AND alias_value REGEXP '^[0-9]+$'
ORDER BY user_id, alias_value
LIMIT 100;
