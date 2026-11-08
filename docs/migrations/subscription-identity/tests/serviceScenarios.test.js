/**
 * 订阅身份 v2 服务层场景回归测试（内存 fake repo + 真实 service/mergeService）。
 * 运行：node docs/migrations/subscription-identity/tests/serviceScenarios.test.js
 * 服务端源码不在 Git 时，用 SERVER_API_DIR 指向 api 目录。
 */
const assert = require('assert');
const path = require('path');

const apiDir = process.env.SERVER_API_DIR
  ? path.resolve(process.env.SERVER_API_DIR)
  : path.resolve(__dirname, '../../../../server');

const { createSubscriptionService } = require(path.join(apiDir, 'subscriptions/service'));
const { createMergeService } = require(path.join(apiDir, 'subscriptions/mergeService'));
const { uuidv4 } = require(path.join(apiDir, 'subscriptions/identity'));
const adapter = require(path.join(apiDir, 'subscriptions/legacyAdapter'));

function createFakeStore() {
  const rows = new Map(); // subscription_id -> row
  const aliases = new Map(); // alias_value -> { user_id, alias_value, alias_type, subscription_id }
  const tombstones = new Map(); // subscription_id -> { user_id, subscription_id, row_snapshot, deleted_at, aliases:Set }
  let nextId = 1;

  function makeRow(userId, input) {
    return {
      id: nextId++,
      user_id: userId,
      subscription_id: input.subscription_id,
      anime_id: String(input.anime_id),
      bangumi_id: input.bangumi_id ?? null,
      remote_sync_id: input.remote_sync_id ?? null,
      anime_title: input.anime_title,
      anime_image: input.anime_image ?? null,
      air_date: input.air_date ?? null,
      is_airing: input.is_airing ?? 0,
      weekday: input.weekday ?? null,
      total_episodes: input.total_episodes ?? 0,
      watched_episodes: input.watched_episodes ?? 0,
      status: input.status ?? 'planning',
      rating: input.rating ?? null,
      notes: input.notes ?? '',
      start_date: input.start_date ?? null,
      finish_date: input.finish_date ?? null,
      current_episodes: input.current_episodes ?? 0,
      client_updated_at: input.client_updated_at ?? null,
      revision: 1,
      updated_at: new Date(),
      subscribed_at: input.subscribed_at ?? new Date()
    };
  }

  function findRow(predicate) {
    return [...rows.values()].find(predicate) || null;
  }

  const repo = {
    async lockBySubscriptionId(conn, userId, subscriptionId) {
      const row = rows.get(subscriptionId);
      return row && row.user_id === userId ? row : null;
    },
    async lockByIds(conn, userId, ids) {
      return ids.map(id => rows.get(id)).filter(row => row && row.user_id === userId);
    },
    async findByBangumiId(conn, userId, bangumiId) {
      return findRow(row => row.user_id === userId && row.bangumi_id === bangumiId);
    },
    async findByRemoteSyncId(conn, userId, remoteSyncId) {
      return findRow(row => row.user_id === userId && row.remote_sync_id === remoteSyncId);
    },
    async findByAnimeId(conn, userId, animeId) {
      return findRow(row => row.user_id === userId && String(row.anime_id) === String(animeId));
    },
    async listByUser(userId) {
      return [...rows.values()].filter(row => row.user_id === userId);
    },
    async listByTitle(conn, userId, title) {
      return [...rows.values()].filter(row => row.user_id === userId && row.anime_title === title);
    },
    async insert(conn, userId, input) {
      const row = makeRow(userId, input);
      rows.set(row.subscription_id, row);
      return row;
    },
    async updateData(conn, userId, subscriptionId, data, clientUpdatedAt) {
      const row = rows.get(subscriptionId);
      Object.assign(row, {
        anime_title: data.animeTitle,
        anime_image: data.animeImage,
        air_date: data.airDate,
        is_airing: data.isAiring ? 1 : 0,
        weekday: data.weekday,
        total_episodes: data.totalEpisodes,
        watched_episodes: data.watchedEpisodes,
        status: data.status,
        rating: data.rating,
        notes: data.notes,
        start_date: data.startDate,
        finish_date: data.finishDate,
        current_episodes: data.currentEpisodes,
        client_updated_at: clientUpdatedAt,
        updated_at: new Date(),
        revision: Number(row.revision || 0) + 1
      });
      return row;
    },
    async updateMergedRow(conn, userId, subscriptionId, rowData, preferredBangumiId, preferredRemoteSyncId, projection) {
      const row = rows.get(subscriptionId);
      Object.assign(row, rowData, {
        bangumi_id: preferredBangumiId,
        remote_sync_id: preferredRemoteSyncId,
        anime_id: String(projection),
        updated_at: new Date(),
        revision: Number(row.revision || 0) + 1
      });
      return row;
    },
    async updateIdentity(conn, userId, subscriptionId, identity) {
      const row = rows.get(subscriptionId);
      if (!row) return null;
      row.bangumi_id = identity.bangumiId ?? null;
      row.remote_sync_id = identity.remoteSyncId ?? null;
      if (identity.animeId != null) row.anime_id = String(identity.animeId);
      return row;
    },
    async assignSubscriptionId(conn, userId, row, subscriptionId) {
      row.subscription_id = subscriptionId;
      rows.set(subscriptionId, row);
      return row;
    },
    async remove(conn, userId, subscriptionId) {
      rows.delete(subscriptionId);
    },
    async insertMergeLog() {},
    toApiSubscription(row, aliasRows) {
      const aliases = aliasRows.map(alias => String(alias.alias_value));
      return {
        subscriptionId: row.subscription_id,
        animeId: row.bangumi_id != null
          ? String(row.bangumi_id)
          : row.remote_sync_id != null
            ? String(row.remote_sync_id)
            : String(row.anime_id),
        animeTitle: row.anime_title,
        watchedEpisodes: Number(row.watched_episodes || 0),
        totalEpisodes: Number(row.total_episodes || 0),
        status: row.status,
        revision: Number(row.revision || 0),
        supersededAnimeIds: aliases.filter(value => value !== String(row.bangumi_id) && value !== String(row.remote_sync_id))
      };
    }
  };

  const aliasRepo = {
    async getByValues(conn, userId, values) {
      return values
        .map(value => aliases.get(value))
        .filter(alias => alias && alias.user_id === userId);
    },
    async getBySubscription(conn, userId, subscriptionId) {
      return [...aliases.values()].filter(alias =>
        alias.user_id === userId && alias.subscription_id === subscriptionId
      );
    },
    async listByUser(userId) {
      return [...aliases.values()].filter(alias => alias.user_id === userId);
    },
    async claim(conn, userId, value, type, subscriptionId) {
      const existing = aliases.get(value);
      if (existing && existing.user_id === userId) {
        if (existing.subscription_id !== subscriptionId) {
          return { conflict: true, existing };
        }
        existing.alias_type = type;
        return { conflict: false, created: false, existing };
      }
      const alias = { user_id: userId, alias_value: value, alias_type: type, subscription_id: subscriptionId };
      aliases.set(value, alias);
      return { conflict: false, created: true, existing: alias };
    },
    async claimRowKey(conn, userId, row) {
      if (!row || !row.anime_id) return null;
      const type = row.bangumi_id != null
        ? 'bangumi'
        : row.remote_sync_id != null
          ? 'remote_sync'
          : 'legacy_local';
      return aliasRepo.claim(conn, userId, String(row.anime_id), type, row.subscription_id);
    },
    async moveTo(conn, userId, fromIds, toId) {
      const ids = new Set(Array.isArray(fromIds) ? fromIds : [fromIds]);
      for (const alias of aliases.values()) {
        if (alias.user_id === userId && ids.has(alias.subscription_id)) {
          alias.subscription_id = toId;
        }
      }
    },
    async deleteBySubscription(conn, userId, subscriptionId) {
      for (const [key, alias] of aliases.entries()) {
        if (alias.user_id === userId && alias.subscription_id === subscriptionId) {
          aliases.delete(key);
        }
      }
    },
    async createTombstone(conn, userId, subscriptionId, row, aliasEntries) {
      const typed = new Map();
      for (const entry of aliasEntries || []) {
        const item = typeof entry === 'string' ? { value: entry, type: null } : entry;
        const value = String(item.value);
        const existing = typed.get(value);
        const stable = item.type === 'bangumi' || item.type === 'remote_sync';
        if (!existing || (!existing.stable && stable)) {
          typed.set(value, { value, type: item.type || null, stable });
        }
      }
      tombstones.set(subscriptionId, {
        user_id: userId,
        subscription_id: subscriptionId,
        row_snapshot: JSON.stringify(row),
        deleted_at: new Date(),
        aliases: typed
      });
    },
    async findTombstonesByValues(conn, userId, values) {
      const output = [];
      for (const tombstone of tombstones.values()) {
        if (tombstone.user_id !== userId) continue;
        for (const value of values) {
          const alias = tombstone.aliases.get(value);
          if (alias) {
            output.push({
              user_id: userId,
              subscription_id: tombstone.subscription_id,
              row_snapshot: tombstone.row_snapshot,
              alias_value: alias.value,
              alias_type: alias.type
            });
            break;
          }
        }
      }
      return output;
    },
    async listTombstones(userId) {
      return [...tombstones.values()]
        .filter(tombstone => tombstone.user_id === userId)
        .flatMap(tombstone => [...tombstone.aliases.values()].map(alias => ({
          subscription_id: tombstone.subscription_id,
          row_snapshot: tombstone.row_snapshot,
          deleted_at: tombstone.deleted_at,
          alias_value: alias.value,
          alias_type: alias.type
        })));
    },
    async getTombstone(conn, userId, subscriptionId) {
      const tombstone = tombstones.get(subscriptionId);
      return tombstone && tombstone.user_id === userId ? tombstone : null;
    },
    async listTombstoneAliases(conn, userId, subscriptionId) {
      const tombstone = tombstones.get(subscriptionId);
      return tombstone && tombstone.user_id === userId
        ? [...tombstone.aliases.values()].map(alias => ({ value: alias.value, type: alias.type }))
        : [];
    },
    async deleteTombstone(conn, userId, subscriptionId) {
      const tombstone = tombstones.get(subscriptionId);
      if (tombstone && tombstone.user_id === userId) tombstones.delete(subscriptionId);
    }
  };

  const conn = {
    async beginTransaction() {},
    async commit() {},
    async rollback() {},
    release() {}
  };
  const db = { promise: () => ({ getConnection: async () => conn }) };

  return { db, repo, aliasRepo, rows, aliases, tombstones };
}

async function main() {
  const store = createFakeStore();
  const mergeService = createMergeService({ repo: store.repo, aliasRepo: store.aliasRepo });
  const service = createSubscriptionService({
    db: store.db,
    repo: store.repo,
    aliasRepo: store.aliasRepo,
    mergeService,
    logger: { log() {}, warn() {}, error() {} }
  });
  const userId = 7;

  // 种子：数字正身行
  store.rows.set('sub-numeric', {
    id: 1, user_id: userId, subscription_id: 'sub-numeric', anime_id: '111',
    bangumi_id: 111, remote_sync_id: null, anime_title: '链锯人',
    total_episodes: 12, watched_episodes: 5, current_episodes: 5, status: 'watching',
    rating: 8, notes: '好看', anime_image: null, air_date: null, is_airing: 1,
    weekday: 3, start_date: null, finish_date: null, client_updated_at: new Date('2026-01-02T00:00:00Z'),
    revision: 1, updated_at: new Date(), subscribed_at: new Date('2026-01-01T00:00:00Z')
  });
  store.aliases.set('111', { user_id: userId, alias_value: '111', alias_type: 'bangumi', subscription_id: 'sub-numeric' });

  // 1) v1 旧设备以 UUID 重传 0 进度幽灵行 → 合并进数字正身，不新增
  const uuid = uuidv4();
  const v1Ghost = adapter.toUpsertInput({
    animeId: uuid, animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 0,
    currentEpisodes: 0, status: 'watching', rating: 0, notes: '', legacyAnimeId: '1'
  });
  const ghostResult = await service.upsert(userId, v1Ghost);
  assert.strictEqual(store.rows.size, 1, 'UUID 幽灵行必须被合并，不能新增');
  assert.strictEqual(store.rows.get('sub-numeric').watched_episodes, 5, '进度不能被 0 集幽灵行拉低');
  assert.strictEqual(ghostResult.subscriptionId, 'sub-numeric', 'UUID 幽灵行应解析到数字正身');
  assert.strictEqual(store.aliases.get(uuid).subscription_id, 'sub-numeric');

  // 2) 同一 UUID 再次全量同步 → 直接命中 alias，仍只有一行
  const ghostAgain = adapter.toUpsertInput({
    animeId: uuid, animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 0,
    currentEpisodes: 0, status: 'watching'
  });
  await service.upsert(userId, ghostAgain);
  assert.strictEqual(store.rows.size, 1);
  assert.strictEqual(store.rows.get('sub-numeric').watched_episodes, 5);

  // 3) 无 alias 的历史行 + 同数字键不同标题 → 数字歧义，忽略且不改动原行
  //    （模拟旧客户端把本地自增 333 当成 Bangumi 333 上报，而 333 已是另一部番）
  store.rows.set('sub-other', {
    id: 2, user_id: userId, subscription_id: 'sub-other', anime_id: '333',
    bangumi_id: 333, remote_sync_id: null, anime_title: '另一部番',
    total_episodes: 24, watched_episodes: 20, current_episodes: 20, status: 'watching',
    rating: 7, notes: '另一部', anime_image: null, air_date: null, is_airing: 0,
    weekday: null, start_date: null, finish_date: null,
    client_updated_at: new Date('2026-01-02T00:00:00Z'), revision: 1, updated_at: new Date()
  });
  const ambiguity = adapter.toUpsertInput({
    animeId: '333', animeTitle: '完全不同的一部番', totalEpisodes: 24, watchedEpisodes: 20, status: 'watching'
  });
  const ambiguous = await service.upsert(userId, ambiguity);
  assert.strictEqual(ambiguous.ignored, true);
  assert.strictEqual(ambiguous.reason, 'identity_blocked');
  assert.strictEqual(store.rows.size, 2);
  assert.strictEqual(store.rows.get('sub-other').anime_title, '另一部番');
  assert.ok(!store.aliases.has('333'), '拦截的键不能写入 alias');

  // 4) 换绑：旧 bangumi 111 → 新 bangumi 222，superseded 旧键进 alias
  const rebind = adapter.toUpsertInput({
    animeId: '222', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 5,
    currentEpisodes: 5, status: 'watching', supersededAnimeIds: ['111']
  });
  await service.upsert(userId, rebind);
  assert.strictEqual(store.rows.size, 2, '换绑不能新增行（sub-other 为场景 3 种子）');
  assert.strictEqual(store.rows.get('sub-numeric').bangumi_id, 222);
  assert.strictEqual(store.rows.get('sub-numeric').anime_id, '222');
  assert.strictEqual(store.aliases.get('111').subscription_id, 'sub-numeric');
  assert.strictEqual(store.aliases.get('222').subscription_id, 'sub-numeric');

  // 5) 旧设备仍以 111 重传 → 命中 alias，不新建
  const oldDevice = adapter.toUpsertInput({
    animeId: '111', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 5,
    currentEpisodes: 5, status: 'watching'
  });
  await service.upsert(userId, oldDevice);
  assert.strictEqual(store.rows.size, 2, '旧设备重传旧键不能新建行');
  assert.strictEqual(store.rows.get('sub-numeric').bangumi_id, 222, '旧键不能把正身身份回退到 111');
  assert.strictEqual(store.rows.get('sub-numeric').anime_id, '222');
  assert.strictEqual(store.rows.get('sub-numeric').rating, 8, '旧设备缺省评分不能清空已有评分');
  assert.strictEqual(store.rows.get('sub-numeric').notes, '好看', '旧设备缺省笔记不能清空已有笔记');

  // 6) 墓碑：删除后相同快照的 sync 被忽略；intent=add 可恢复
  const removeResult = await service.remove(userId, adapter.toRemoveInput({ animeId: '222' }));
  assert.strictEqual(removeResult.removed, true);
  assert.strictEqual(store.rows.size, 1, '只删除目标行，不影响其他番剧');
  assert.ok(!store.rows.has('sub-numeric'));
  assert.ok(store.tombstones.has('sub-numeric'));

  const resend = adapter.toUpsertInput({
    animeId: '222', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 5,
    currentEpisodes: 5, status: 'watching', rating: 8, notes: '好看'
  });
  const ignored = await service.upsert(userId, resend);
  assert.strictEqual(ignored.ignored, true);
  assert.strictEqual(store.rows.size, 1);

  const readd = await service.upsert(userId, {
    ...resend,
    intent: 'add'
  });
  assert.strictEqual(store.rows.size, 2);
  assert.strictEqual(store.rows.get('sub-numeric').anime_title, '链锯人');
  assert.ok(!store.tombstones.has('sub-numeric'));

  // 7) LWW：较旧的客户端时间不覆盖新进度；显式 progressReset 才能降低
  store.rows.get('sub-numeric').client_updated_at = new Date('2026-01-05T00:00:00Z');
  store.rows.get('sub-numeric').watched_episodes = 8;
  const stale = adapter.toUpsertInput({
    animeId: '222', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 0, status: 'watching'
  });
  stale.clientUpdatedAt = '2026-01-04T00:00:00Z';
  const staleResult = await service.upsert(userId, stale);
  assert.strictEqual(staleResult.applied, false);
  assert.strictEqual(store.rows.get('sub-numeric').watched_episodes, 8);

  const reset = adapter.toUpsertInput({
    animeId: '222', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 0, status: 'watching'
  });
  reset.clientUpdatedAt = '2026-01-06T00:00:00Z';
  reset.progressReset = true;
  await service.upsert(userId, reset);
  assert.strictEqual(store.rows.get('sub-numeric').watched_episodes, 0);

  // 8) v1 无 clientUpdatedAt 的低进度同步：到达时间较新，但服务端禁止下调
  store.rows.get('sub-numeric').watched_episodes = 8;
  const lower = adapter.toUpsertInput({
    animeId: '222', animeTitle: '链锯人', totalEpisodes: 12, watchedEpisodes: 2, status: 'watching'
  });
  await service.upsert(userId, lower);
  assert.strictEqual(store.rows.get('sub-numeric').watched_episodes, 8);

  // 9) UUID+UUID 进度指纹相同 → 不自动合并（保留两条候选，等人工确认）
  const uuidA = uuidv4();
  const uuidB = uuidv4();
  await service.upsert(userId, {
    identity: { remoteSyncId: uuidA },
    data: { animeTitle: '同指纹A', totalEpisodes: 12, watchedEpisodes: 3, status: 'watching' }
  });
  await service.upsert(userId, {
    identity: { remoteSyncId: uuidB },
    data: { animeTitle: '同指纹B', totalEpisodes: 12, watchedEpisodes: 3, status: 'watching' }
  });
  assert.strictEqual(store.rows.size, 4); // sub-other + 数字行 + 两个 UUID 行

  // 10) 通过 subscriptionId 换绑到新 Bangumi：旧 UUID 自动登记 alias，
  //     旧设备以旧 UUID 重传不能把身份回退、也不能新建行
  const uuidC = uuidv4();
  const uuidRow = await service.upsert(userId, {
    identity: { remoteSyncId: uuidC },
    data: { animeTitle: 'UUID番剧', totalEpisodes: 12, watchedEpisodes: 4, status: 'watching' }
  });
  const uuidSubId = uuidRow.subscriptionId;
  store.aliases.delete(uuidC); // 模拟历史行未登记行键 alias
  await service.upsert(userId, {
    subscriptionId: uuidSubId,
    identity: { bangumiId: 999 },
    data: { animeTitle: 'UUID番剧', totalEpisodes: 12, watchedEpisodes: 4, status: 'watching' }
  });
  assert.strictEqual(store.rows.get(uuidSubId).bangumi_id, 999);
  assert.strictEqual(store.rows.get(uuidSubId).remote_sync_id, null);
  assert.strictEqual(
    store.aliases.get(uuidC).subscription_id,
    uuidSubId,
    '身份迁移后旧 UUID 必须登记 alias'
  );

  const oldUuidDevice = adapter.toUpsertInput({
    animeId: uuidC, animeTitle: 'UUID番剧', totalEpisodes: 12, watchedEpisodes: 4, status: 'watching'
  });
  await service.upsert(userId, oldUuidDevice);
  assert.strictEqual(store.rows.size, 5, '旧 UUID 重传不能新建行');
  assert.strictEqual(store.rows.get(uuidSubId).bangumi_id, 999, '旧 UUID 重传不能把身份回退');

  // 11) v2 删除请求丢弃数字 legacyKeys：本地自增 id 可能与其他番的 Bangumi ID 撞车，
  //     删除接口没有标题可校验，数字 legacyKey 一律不收；UUID legacyKey 保留（会被升级为 remote_sync）
  const removeInput = adapter.toV2RemoveInput({
    identity: { remoteSyncId: uuidC },
    legacyKeys: ['12345', uuidA]
  });
  assert.deepStrictEqual(removeInput.legacyKeys, [uuidA], '数字 legacyKey 必须被丢弃，UUID 保留');

  // 12) 墓碑别名带类型：旧本地 id 命中必须标题一致才采信
  const uuidLegacy = uuidv4();
  const legacyRow = await service.upsert(userId, {
    identity: { remoteSyncId: uuidLegacy },
    data: { animeTitle: '别名类型测试', totalEpisodes: 12, watchedEpisodes: 6, status: 'watching' }
  });
  await service.remove(userId, {
    identity: { remoteSyncId: uuidLegacy },
    legacyKeys: ['4242']
  });
  const typedTombstone = store.tombstones.get(legacyRow.subscriptionId);
  assert.strictEqual(typedTombstone.aliases.get('4242').type, 'legacy_local');
  assert.strictEqual(typedTombstone.aliases.get(uuidLegacy).type, 'remote_sync');

  const sameTitleReadd = adapter.toUpsertInput({
    animeId: '4242', animeTitle: '别名类型测试', totalEpisodes: 12,
    watchedEpisodes: 6, currentEpisodes: 0, status: 'watching'
  });
  const titleMatched = await service.upsert(userId, sameTitleReadd);
  assert.strictEqual(titleMatched.ignored, true, '旧本地 id + 标题一致才允许命中墓碑');

  const differentTitleReadd = adapter.toUpsertInput({
    animeId: '4242', animeTitle: '完全不同的番', totalEpisodes: 12,
    watchedEpisodes: 6, status: 'watching'
  });
  await service.upsert(userId, differentTitleReadd);
  const createdByLegacyNumeric = [...store.rows.values()].find(
    row => row.anime_title === '完全不同的番'
  );
  assert.ok(createdByLegacyNumeric, '标题不一致的旧本地 id 不能命中别人的墓碑');
  assert.notStrictEqual(createdByLegacyNumeric.subscription_id, legacyRow.subscriptionId);

  // 13) 只有标题相同时不能触发墓碑兜底：请求必须带旧键才允许标题兜底
  const uuidTitle = uuidv4();
  const titleRow = await service.upsert(userId, {
    identity: { remoteSyncId: uuidTitle },
    data: { animeTitle: '标题兜底番', totalEpisodes: 12, watchedEpisodes: 2, status: 'watching' }
  });
  await service.remove(userId, {
    identity: { remoteSyncId: uuidTitle },
    legacyKeys: ['6666']
  });
  const beforeTitleOnly = store.rows.size;
  await service.upsert(userId, {
    identity: { bangumiId: 888 },
    data: { animeTitle: '标题兜底番', totalEpisodes: 12, watchedEpisodes: 2, status: 'watching' }
  });
  assert.strictEqual(store.rows.size, beforeTitleOnly + 1, '无旧键时标题不能触发墓碑恢复');
  assert.ok(
    [...store.rows.values()].some(row => row.bangumi_id === 888),
    '不同稳定键应作为新订阅写入'
  );
  assert.ok(store.tombstones.has(titleRow.subscriptionId), '墓碑必须保留，等待旧键重传');

  // 14) merged 响应必须带 loser 的 typed aliases（客户端用它清理本地重复卡）
  const mergeA = uuidv4();
  const mergeB = uuidv4();
  store.rows.set('merge-a', {
    id: 90, user_id: userId, subscription_id: 'merge-a', anime_id: mergeA,
    bangumi_id: null, remote_sync_id: mergeA, anime_title: '合并A',
    total_episodes: 12, watched_episodes: 1, current_episodes: 1, status: 'watching',
    rating: null, notes: '', anime_image: null, air_date: null, is_airing: 0,
    weekday: null, start_date: null, finish_date: null,
    client_updated_at: new Date(), revision: 1, updated_at: new Date(),
    subscribed_at: new Date('2026-01-01T00:00:00Z')
  });
  store.rows.set('merge-b', {
    id: 91, user_id: userId, subscription_id: 'merge-b', anime_id: mergeB,
    bangumi_id: null, remote_sync_id: mergeB, anime_title: '合并B',
    total_episodes: 12, watched_episodes: 9, current_episodes: 9, status: 'watching',
    rating: null, notes: '', anime_image: null, air_date: null, is_airing: 0,
    weekday: null, start_date: null, finish_date: null,
    client_updated_at: new Date(), revision: 1, updated_at: new Date(),
    subscribed_at: new Date('2026-01-02T00:00:00Z')
  });
  store.aliases.set(mergeA, {
    user_id: userId, alias_value: mergeA, alias_type: 'remote_sync', subscription_id: 'merge-a'
  });
  store.aliases.set(mergeB, {
    user_id: userId, alias_value: mergeB, alias_type: 'remote_sync', subscription_id: 'merge-b'
  });
  const merged = await service.mergeForUser(userId, ['merge-a', 'merge-b'], 'test_merge');
  assert.ok(merged, '合并必须返回结果');
  assert.ok(merged.merged.length >= 1, 'merged 必须包含被合并订阅');
  const mergedEntry = merged.merged[0];
  const mergedAliasValues = mergedEntry.aliases.map(alias => alias.value);
  assert.ok(
    mergedAliasValues.includes(mergeA) || mergedAliasValues.includes(mergeB),
    'merged.aliases 必须包含 loser 的旧键'
  );
  assert.ok(
    mergedEntry.aliases.every(alias => alias.type),
    'merged.aliases 必须带类型'
  );

  console.log('service scenario tests passed');
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
