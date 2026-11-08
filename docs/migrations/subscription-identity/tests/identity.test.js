/**
 * 订阅身份 v2 纯函数回归测试。
 * 运行：node docs/migrations/subscription-identity/tests/identity.test.js
 * 服务端源码不在 Git 时，用 SERVER_API_DIR 指向 api 目录。
 */
const assert = require('assert');
const path = require('path');

const apiDir = process.env.SERVER_API_DIR
  ? path.resolve(process.env.SERVER_API_DIR)
  : path.resolve(__dirname, '../../../../server');

const { createSubscriptionService } = require(path.join(apiDir, 'subscriptions/service'));
const adapter = require(path.join(apiDir, 'subscriptions/legacyAdapter'));
const identity = require(path.join(apiDir, 'subscriptions/identity'));

const service = createSubscriptionService({
  db: { promise: () => ({}) },
  repo: {},
  aliasRepo: {},
  mergeService: {},
  logger: console
});

// 1) v1 数字 Bangumi + 本地 id + superseded 旧键
(() => {
  const input = adapter.toUpsertInput({
    animeId: '321885',
    animeTitle: '链锯人',
    legacyAnimeId: '12',
    supersededAnimeIds: ['11111111-2222-4333-8444-555555555555', '111']
  });
  const keys = service.splitKeys(input);
  assert.deepStrictEqual(keys.stable, [{ type: 'bangumi', value: '321885' }]);
  assert.deepStrictEqual(keys.legacy, [{ type: 'legacy_local', value: '12' }]);
  assert.deepStrictEqual(keys.superseded, [
    { type: 'remote_sync', value: '11111111-2222-4333-8444-555555555555', trusted: true },
    { type: 'bangumi', value: '111', trusted: true }
  ]);
  assert.strictEqual(input.intent, 'sync');
  assert.strictEqual(input.legacyTitleConvergence, true);
})();

// 2) animeId 与 legacyAnimeId 相同：不能把本地 id 写进 legacy
(() => {
  const input = adapter.toUpsertInput({ animeId: '321885', animeTitle: '链锯人', legacyAnimeId: '321885' });
  const keys = service.splitKeys(input);
  assert.deepStrictEqual(keys.legacy, []);
})();

// 3) UUID 主键 + 本地 id + superseded
(() => {
  const uuid = '11111111-2222-4333-8444-555555555555';
  const input = adapter.toUpsertInput({
    animeId: uuid,
    animeTitle: '某番',
    legacyAnimeId: '7',
    supersededAnimeIds: [uuid, '999']
  });
  const keys = service.splitKeys(input);
  assert.deepStrictEqual(keys.stable, [{ type: 'remote_sync', value: uuid }]);
  assert.deepStrictEqual(keys.legacy, [{ type: 'legacy_local', value: '7' }]);
  assert.deepStrictEqual(keys.superseded, [{ type: 'bangumi', value: '999', trusted: true }]);
})();

// 4) 非法 v1 请求不产生 identity
(() => {
  assert.strictEqual(adapter.toUpsertInput({ animeId: '0', animeTitle: 'x' }), null);
  assert.strictEqual(adapter.toUpsertInput({ animeId: 5, animeTitle: '  ' }), null);
  assert.strictEqual(adapter.toRemoveInput({}), null);
})();

// 5) v2 输入保留显式 superseded 类型
(() => {
  const input = adapter.toV2UpsertInput({
    identity: { bangumiId: 321885, remoteSyncId: null },
    legacyKeys: ['12'],
    supersededKeys: [{ type: 'remote_sync', value: 'old-uuid' }],
    intent: 'add',
    data: { animeTitle: '链锯人' }
  });
  const keys = service.splitKeys(input);
  assert.deepStrictEqual(keys.stable, [{ type: 'bangumi', value: '321885' }]);
  assert.deepStrictEqual(keys.legacy, [{ type: 'legacy_local', value: '12' }]);
  assert.deepStrictEqual(keys.superseded, [
    { type: 'remote_sync', value: 'old-uuid', trusted: true }
  ]);
  assert.strictEqual(input.intent, 'add');
})();

// 6) 客户端时间：未来超过 5 分钟按 now，无法解析按 now
(() => {
  const now = Date.parse('2026-10-29T12:00:00.000Z');
  assert.strictEqual(
    identity.parseClientTimestamp('2026-10-29T13:00:00.000Z', now).getTime(),
    now
  );
  assert.strictEqual(identity.parseClientTimestamp('bad', now).getTime(), now);
  assert.strictEqual(
    identity.parseClientTimestamp('2026-10-29T11:59:00.000Z', now).getTime(),
    now - 60000
  );
})();

// 7) 墓碑快照：完全一致才拦截
(() => {
  const data = identity.normalizeSubscriptionData({
    animeTitle: '链锯人',
    totalEpisodes: 12,
    watchedEpisodes: 5,
    currentEpisodes: 5,
    status: 'watching',
    rating: 8.5,
    notes: '好看'
  });
  const snapshot = {
    anime_title: '链锯人', total_episodes: 12, watched_episodes: 5,
    current_episodes: 5, status: 'watching', rating: 8.5, notes: '好看'
  };
  assert.strictEqual(identity.matchesTombstoneSnapshot(data, snapshot), true);
  assert.strictEqual(
    identity.matchesTombstoneSnapshot({ ...data, watchedEpisodes: 6 }, snapshot),
    false
  );
})();

console.log('subscription unit tests passed');
