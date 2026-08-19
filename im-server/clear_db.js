// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const fs = require('fs');
const path = require('path');
const config = require('./config');

async function clearDatabase() {
    console.log(`[*] Starting database cleanup for engine: ${config.dbType.toUpperCase()}`);

    if (config.dbType === 'sqlite') {
        const dbFile = path.resolve(process.cwd(), config.sqlite.filename);
        const walFile = `${dbFile}-wal`;
        const shmFile = `${dbFile}-shm`;

        if (!fs.existsSync(dbFile)) {
            console.log('  [i] No SQLite database file found.');
            console.log('[+] SQLite database is already clean!');
            return;
        }

        // 1. 尝试直接物理删除文件
        let deletedPhysically = false;
        try {
            [walFile, shmFile, dbFile].forEach(file => {
                if (fs.existsSync(file)) {
                    fs.unlinkSync(file);
                    console.log(`  [-] Deleted file: ${path.basename(file)}`);
                }
            });
            deletedPhysically = true;
        } catch (e) {
            // 若 Windows 文件被持有句柄锁定，通过 SQL 事务清空数据并执行 VACUUM
            console.log(`  [i] Direct file deletion locked (${e.message}), executing SQL table truncate...`);
        }

        // 2. 如果无法物理删除，使用 SQLite 原生 SQL 清空所有表并收缩文件
        if (!deletedPhysically && fs.existsSync(dbFile)) {
            const Database = require('better-sqlite3');
            const db = new Database(dbFile);
            db.pragma('journal_mode = WAL');
            db.exec(`
                DELETE FROM sync_events;
                DELETE FROM messages;
                DELETE FROM sessions;
                DELETE FROM user_cursors;
                VACUUM;
            `);
            db.close();
            console.log('  [-] Cleared all records from tables (sessions, messages, sync_events, user_cursors)');
        }

        console.log('[+] SQLite database successfully reset!');
    } else if (config.dbType === 'mysql') {
        const mysql = require('mysql2/promise');
        const { host, port, user, password, database } = config.mysql;

        try {
            const conn = await mysql.createConnection({ host, port, user, password, database });
            console.log(`  [+] Connected to MySQL: ${user}@${host}:${port}/${database}`);

            const tables = ['sync_events', 'messages', 'sessions', 'user_cursors'];
            for (const table of tables) {
                await conn.query(`TRUNCATE TABLE \`${table}\``);
                console.log(`  [-] Truncated table: \`${table}\``);
            }

            await conn.end();
            console.log('[+] MySQL database tables successfully cleared!');
        } catch (err) {
            console.error(`[!] Failed to clear MySQL database: ${err.message}`);
            process.exit(1);
        }
    } else {
        console.error(`[!] Unsupported dbType: ${config.dbType}`);
        process.exit(1);
    }
}

clearDatabase().catch(err => {
    console.error(`[FATAL] ${err.message}`);
    process.exit(1);
});
