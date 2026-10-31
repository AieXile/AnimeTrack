#!/usr/bin/env node
/**
 * 订阅身份 v2 阶段 2 回填脚本（逐用户事务）。
 *
 * 用法（在服务器上）：
 *   cd /www/wwwroot/api
 *   node /path/to/docs/migrations/subscription-identity/002_backfill.js --dry-run
 *   node /path/to/docs/migrations/subscription-identity/002_backfill.js --execute
 *
 * 必填环境变量：
 *   DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME
 * 可选：
 *   SERVER_API_DIR  服务端 api 目录（默认仓库内 ../../../.trae/server/api）
 *
 * 安全约定：
 * - 默认 dry-run：每个用户事务执行完立即回滚，不落库；
 * - --execute 才会提交；
 * - 回填期间必须先打开维护窗口（SUBSCRIPTION_WRITE_LOCK=1 或锁文件），
 *   确保没有客户端写入同一用户。
 */
const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const args = { execute: false, skipSchema: false, userIds: null, dryRun: true };
  for (let i = 0; i < argv.length; i++) {
    const item = argv[i];
    if (item === '--execute') {
      args.execute = true;
      args.dryRun = false;
    } else if (item === '--dry-run') {
      args.execute = false;
      args.dryRun = true;
    } else if (item === '--skip-schema') {
      args.skipSchema = true;
    } else if (item === '--user-ids') {
      const value = argv[++i] || '';
      args.userIds = value.split(',').map(v => Number(v.trim())).filter(Number.isFinite);
    } else if (item === '--config') {
      args.config = argv[++i];
    } else if (item === '--server-api') {
      args.serverApi = argv[++i];
    }
  }
  return args;
}

function loadDbConfig(args) {
  if (args.config) {
    const configPath = path.resolve(args.config);
    return JSON.parse(fs.readFileSync(configPath, 'utf8'));
  }
  return {
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME || 'animetrack'
  };
}

function resolveServerApi(args) {
  return path.resolve(
    args.serverApi ||
    process.env.SERVER_API_DIR ||
    path.join(__dirname, '..', '..', '..', '.trae', 'server', 'api')
  );
}

function loadModules(serverApiDir) {
  try {
    require.resolve('mysql2/promise');
  } catch (error) {
    const message = [
      '无法加载 mysql2。请在服务端目录运行，或设置 NODE_PATH：',
      '  NODE_PATH=/www/wwwroot/api/node_modules node <本脚本>',
      '也可以先 cd 到服务端目录再执行。'
    ].join('\n');
    throw new Error(`${message}\n原始错误: ${error.message}`);
  }

  // 服务端仓储模块统一通过 db.promise() 取 promise 连接池，
  // 这里必须创建回调式 pool；mysql2/promise 的 pool 没有 .promise()。
  const mysql = require('mysql2');
  const schema = require(path.join(serverApiDir, 'subscriptions', 'schema'));
  const backfill = require(path.join(serverApiDir, 'subscriptions', 'backfill'));
  return { mysql, schema, backfill };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const dbConfig = loadDbConfig(args);
  const serverApiDir = resolveServerApi(args);
  const { mysql, schema, backfill } = loadModules(serverApiDir);

  if (!args.skipSchema) {
    console.log('阶段 1：确保订阅身份表结构（幂等）...');
  }

  const db = mysql.createPool({
    ...dbConfig,
    waitForConnections: true,
    connectionLimit: 5
  });

  try {
    if (!args.skipSchema) {
      await schema.ensureSubscriptionSchema(db);
    }

    console.log(
      args.dryRun
        ? '阶段 2：DRY-RUN（事务执行后回滚，不写入任何数据）'
        : '阶段 2：EXECUTE（逐用户提交，请确认维护窗口已开启）'
    );

    const result = await backfill.backfillAll({
      db,
      dryRun: args.dryRun,
      userIds: args.userIds,
      logger: console
    });

    console.log('回填汇总:', JSON.stringify(result, null, 2));
    if (result.errors.length > 0) {
      process.exitCode = 1;
    }
    if (args.dryRun) {
      console.log('提示：确认汇总无误后，使用 --execute 正式写入。');
    }
  } finally {
    await db.end();
  }
}

main().catch(error => {
  console.error('回填脚本失败:', error.message);
  process.exitCode = 1;
});
