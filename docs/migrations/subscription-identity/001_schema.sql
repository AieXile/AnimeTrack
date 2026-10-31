-- 订阅身份 v2：阶段 1 DDL（全部可空 / 新表 / 普通索引）
-- 说明：
-- 1. 生产环境可先启动新版 server.js，由 subscriptions/schema.js 幂等执行同样的 DDL；
-- 2. 本文件用于人工预检或无法启动服务时手工执行；
-- 3. 唯一约束不在这里加，等阶段 2 回填 + 003_verify.sql 通过后，再用
--    SUBSCRIPTION_V2_STRICT=1 启动服务，由 schema.js 的 ensureStrictConstraints 添加。
-- 4. ADD COLUMN 在列已存在时会报错，重复执行前请先核对 information_schema。

ALTER TABLE user_subscriptions
  ADD COLUMN subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '服务端权威订阅 ID',
  ADD COLUMN bangumi_id INT NULL
    COMMENT '正身 Bangumi 条目 ID',
  ADD COLUMN remote_sync_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '正身客户端同步 UUID',
  ADD COLUMN client_updated_at DATETIME(3) NULL
    COMMENT '客户端最后有效修改时间',
  ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '写入版本号',
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3)
    COMMENT '服务端写入时间';

ALTER TABLE user_subscriptions
  ADD KEY idx_user_subscription (user_id, subscription_id);

CREATE TABLE IF NOT EXISTS subscription_aliases (
  user_id INT UNSIGNED NOT NULL COMMENT '用户 ID',
  alias_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '历史键/设备键',
  alias_type ENUM('bangumi','remote_sync','legacy_local') NOT NULL COMMENT '键语义',
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '指向的正身订阅',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, alias_value),
  KEY idx_subscription (user_id, subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='订阅键别名：任意旧键解析到唯一订阅';

CREATE TABLE IF NOT EXISTS subscription_tombstones (
  user_id INT UNSIGNED NOT NULL,
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  row_snapshot JSON NULL COMMENT '删除时的整行快照，用于恢复/比对',
  deleted_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, subscription_id),
  KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅删除墓碑';

CREATE TABLE IF NOT EXISTS subscription_tombstone_aliases (
  user_id INT UNSIGNED NOT NULL,
  alias_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  alias_type ENUM('bangumi','remote_sync','legacy_local') NULL
    COMMENT '键语义，历史行为 NULL',
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (user_id, alias_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='墓碑别名索引：拦截旧键重传';

CREATE TABLE IF NOT EXISTS subscription_merge_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  target_subscription_id CHAR(36) NOT NULL,
  merged_subscription_id CHAR(36) NULL,
  reason VARCHAR(64) NOT NULL,
  source_row JSON NOT NULL,
  target_row JSON NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='订阅合并审计（可回滚）';
