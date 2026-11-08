/**
 * 真实 MySQL 集成测试（默认跳过）。
 *
 * 用法（在 server/ 下已安装依赖后）：
 *   PowerShell:
 *     $env:MYSQL_URL='mysql://root:pass@127.0.0.1:3306/'
 *     node docs/migrations/subscription-identity/tests/integration.mysql.test.js
 *
 * 覆盖：
 * - v2 upsert 建行 + alias 登记
 * - 删除写入 typed 墓碑、旧本地键保存为 legacy_local
 * - 完全一致的旧设备重传被 ignored
 * - intent=add 恢复并保留全部别名
 * - 并发同键 upsert 最终只剩一行
 * - mergeForUser 返回 loser 的 typed aliases
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { createRequire } = require('module');

const MYSQL_URL = process.env.MYSQL_URL;
if (!MYSQL_URL) {
  console.log('SKIP integration.mysql.test.js：未设置 MYSQL_URL');
  process.exit(0);
}

const repoRoot = path.resolve(__dirname, '../../../..');
const serverApiDir = path.join(repoRoot, 'server');
const serverRequire = createRequire(path.join(serverApiDir, 'package.json'));
const mysql = serverRequire('mysql2/promise');

const { createSubscriptionRepository } = require(path.join(serverApiDir, 'subscriptions/subscriptionRepository'));
const { createAliasRepository } = require(path.join(serverApiDir, 'subscriptions/aliasRepository'));
const { createMergeService } = require(path.join(serverApiDir, 'subscriptions/mergeService'));
const { createSubscriptionService } = require(path.join(serverApiDir, 'subscriptions/service'));
const adapter = require(path.join(serverApiDir, 'subscriptions/legacyAdapter'));

const BASE_DDL = `
CREATE TABLE user_subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  anime_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  anime_title VARCHAR(255) NOT NULL DEFAULT '',
  anime_image VARCHAR(512) NULL,
  air_date DATE NULL,
  is_airing TINYINT(1) NOT NULL DEFAULT 0,
  weekday TINYINT NULL,
  total_episodes INT NOT NULL DEFAULT 0,
  watched_episodes INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'planning',
  rating DECIMAL(3,1) NULL,
  notes TEXT NULL,
  start_date DATE NULL,
  finish_date DATE NULL,
  current_episodes INT NOT NULL DEFAULT 0,
  subscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_user_anime (user_id, anime_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`;

function dbOptions(url, database) {
  const parsed = new URL(url);
  return {
    host: parsed.hostname,
    port: Number(parsed.port || 3306),
    user: decodeURIComponent(parsed.username),
    password: decodeURIComponent(parsed.password),
    database,
    multipleStatements: true,
    connectionLimit: 10
  };
}

async function main() {
  const database = `animetrack_it_${Date.now()}`;
  const admin = await mysql.createConnection(dbOptions(MYSQL_URL, ''));
  await admin.query(`CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4`);
  const pool = mysql.createPool(dbOptions(MYSQL_URL, database));

  try {
    await pool.query(BASE_DDL);
    const schemaSql = fs.readFileSync(
      path.join(repoRoot, 'docs/migrations/subscription-identity/001_schema.sql'),
      'utf8'
    );
    // 001_schema.sql 已在全新库中创建 alias_type；006 只用于生产存量库，这里不执行。
    await pool.query(schemaSql);

    const repo = createSubscriptionRepository(pool);
    const aliasRepo = createAliasRepository(pool);
    const mergeService = createMergeService(repo, aliasRepo);
    const service = createSubscriptionService({
      db: pool,
      repo,
      aliasRepo,
      mergeService,
      logger: { info() {}, warn() {}, error: console.error }
    });

    const userId = 1;

    // 1. v2 upsert
    const first = await service.upsert(userId, adapter.toV2UpsertInput({
      identity: { bangumiId: 100 },
      intent: 'add',
      clientUpdatedAt: new Date().toISOString(),
      data: { animeTitle: '集成测试番', totalEpisodes: 12, watchedEpisodes: 3, status: 'watching' }
    }));
    assert.ok(first.subscriptionId, 'upsert 必须返回 subscriptionId');
    const aliases = await aliasRepo.getBySubscription(null, userId, first.subscriptionId);
    assert.ok(aliases.some(alias => alias.alias_type === 'bangumi' && alias.alias_value === '100'));

    // 2. 删除 → typed 墓碑
    // 注意：v2 delete 会过滤数字 legacyKey（防止按 anime_id 误删），
    // 这里用 UUID 形状的旧键验证 legacy_local 类型写入。
    const legacyKey = 'legacy-uuid-7777';
    const removeResult = await service.remove(userId, adapter.toV2RemoveInput({
      identity: { bangumiId: 100 },
      legacyKeys: [legacyKey]
    }));
    assert.strictEqual(removeResult.removed, true);
    const [tombstoneAliases] = await pool.query(
      `SELECT alias_value, alias_type FROM subscription_tombstone_aliases
       WHERE user_id = ? AND subscription_id = ? ORDER BY alias_value`,
      [userId, first.subscriptionId]
    );
    const byValue = new Map(tombstoneAliases.map(row => [row.alias_value, row.alias_type]));
    assert.strictEqual(byValue.get('100'), 'bangumi');
    assert.strictEqual(byValue.get(legacyKey), 'legacy_local');

    // 3. 旧设备完全一致的重传 → ignored
    const ignored = await service.upsert(userId, adapter.toUpsertInput({
      animeId: '100',
      animeTitle: '集成测试番',
      totalEpisodes: 12,
      watchedEpisodes: 3,
      currentEpisodes: 0,
      status: 'watching'
    }));
    assert.strictEqual(ignored.ignored, true);

    // 4. intent=add 恢复，且墓碑里的旧键全部搬回
    const restored = await service.upsert(userId, adapter.toV2UpsertInput({
      identity: { bangumiId: 100 },
      intent: 'add',
      clientUpdatedAt: new Date().toISOString(),
      data: { animeTitle: '集成测试番', totalEpisodes: 12, watchedEpisodes: 4, status: 'watching' }
    }));
    assert.strictEqual(restored.ignored, false);
    assert.strictEqual(restored.subscriptionId, first.subscriptionId, '恢复必须复用墓碑订阅 ID');
    const restoredAliases = await aliasRepo.getBySubscription(null, userId, first.subscriptionId);
    assert.ok(
      restoredAliases.some(
        alias => alias.alias_value === legacyKey && alias.alias_type === 'legacy_local'
      )
    );

    // 5. 并发同键 upsert：最终仍只有一行
    const inputs = Array.from({ length: 5 }, (_, index) =>
      adapter.toV2UpsertInput({
        identity: { bangumiId: 200 },
        intent: 'add',
        clientUpdatedAt: new Date(Date.now() + index).toISOString(),
        data: {
          animeTitle: '并发番',
          totalEpisodes: 12,
          watchedEpisodes: index,
          status: 'watching'
        }
      })
    );
    const concurrent = await Promise.allSettled(inputs.map(input => service.upsert(userId, input)));
    assert.ok(
      concurrent.some(item => item.status === 'fulfilled'),
      '并发 upsert 至少要有一次成功'
    );
    const [concurrentRows] = await pool.query(
      'SELECT COUNT(*) AS count FROM user_subscriptions WHERE user_id = ? AND bangumi_id = 200',
      [userId]
    );
    assert.strictEqual(concurrentRows[0].count, 1, '并发同键最终必须收敛为一行');

    // 6. 合并返回 typed aliases
    const left = await service.upsert(userId, adapter.toV2UpsertInput({
      identity: { remoteSyncId: 'uuid-left' },
      intent: 'add',
      clientUpdatedAt: new Date().toISOString(),
      data: { animeTitle: '合并左', totalEpisodes: 12, watchedEpisodes: 1, status: 'watching' }
    }));
    const right = await service.upsert(userId, adapter.toV2UpsertInput({
      identity: { remoteSyncId: 'uuid-right' },
      intent: 'add',
      clientUpdatedAt: new Date().toISOString(),
      data: { animeTitle: '合并右', totalEpisodes: 12, watchedEpisodes: 9, status: 'watching' }
    }));
    const merged = await service.mergeForUser(
      userId,
      [left.subscriptionId, right.subscriptionId],
      'integration_merge'
    );
    assert.ok(merged.merged.length >= 1);
    const mergedAliases = merged.merged[0].aliases;
    assert.ok(
      mergedAliases.some(alias => alias.type === 'remote_sync' && /uuid-(left|right)/.test(alias.value)),
      'merged.aliases 必须带 loser 的 remote_sync 键'
    );

    console.log('integration.mysql.test.js passed');
  } finally {
    await pool.end().catch(() => {});
    await admin.query(`DROP DATABASE IF EXISTS \`${database}\``).catch(() => {});
    await admin.end().catch(() => {});
  }
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
