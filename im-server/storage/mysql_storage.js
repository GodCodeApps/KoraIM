// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const mysql = require('mysql2/promise');
const BaseStorage = require('./base_storage');

class MysqlStorage extends BaseStorage {
    constructor(config) {
        super();
        this.config = config;
        this.pool = null;
    }

    async init() {
        const { host, port, user, password, database, connectionLimit } = this.config.mysql;

        // 1. 确保数据库存在（若无则创建）
        const rootConn = await mysql.createConnection({
            host,
            port,
            user,
            password
        });
        await rootConn.query(`CREATE DATABASE IF NOT EXISTS \`${database}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
        await rootConn.end();

        // 2. 初始化连接池
        this.pool = mysql.createPool({
            host,
            port,
            user,
            password,
            database,
            waitForConnections: true,
            connectionLimit: connectionLimit || 10,
            queueLimit: 0
        });

        // 3. 创建表结构
        await this._createTables();
        console.log(`[+] MySQL storage initialized: ${user}@${host}:${port}/${database}`);
    }

    async _createTables() {
        const createSessionsTable = `
            CREATE TABLE IF NOT EXISTS \`sessions\` (
                \`session_id\` VARCHAR(64) NOT NULL,
                \`session_type\` INT NOT NULL DEFAULT 1,
                \`user_a\` VARCHAR(64) NOT NULL,
                \`user_b\` VARCHAR(64) NOT NULL,
                \`last_message_id\` VARCHAR(64) NOT NULL DEFAULT '',
                \`last_msg_preview\` VARCHAR(255) NOT NULL DEFAULT '',
                \`last_msg_time\` BIGINT NOT NULL DEFAULT 0,
                \`created_at\` BIGINT NOT NULL,
                \`updated_at\` BIGINT NOT NULL,
                PRIMARY KEY (\`session_id\`),
                UNIQUE KEY \`uk_p2p_users\` (\`user_a\`, \`user_b\`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createMessagesTable = `
            CREATE TABLE IF NOT EXISTS \`messages\` (
                \`message_id\` VARCHAR(64) NOT NULL,
                \`session_id\` VARCHAR(64) NOT NULL,
                \`session_type\` INT NOT NULL DEFAULT 1,
                \`sender_id\` VARCHAR(64) NOT NULL,
                \`receiver_id\` VARCHAR(64) NOT NULL,
                \`msg_type\` INT NOT NULL DEFAULT 1,
                \`attachment\` TEXT NOT NULL,
                \`extra\` TEXT NOT NULL,
                \`status\` INT NOT NULL DEFAULT 1,
                \`send_time\` BIGINT NOT NULL,
                \`created_at\` BIGINT NOT NULL,
                \`updated_at\` BIGINT NOT NULL DEFAULT 0,
                \`recalled\` TINYINT NOT NULL DEFAULT 0,
                \`recalled_at\` BIGINT NOT NULL DEFAULT 0,
                \`recalled_by\` VARCHAR(64) NOT NULL DEFAULT '',
                PRIMARY KEY (\`message_id\`),
                KEY \`idx_session_time\` (\`session_id\`, \`send_time\` DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createSyncEventsTable = `
            CREATE TABLE IF NOT EXISTS \`sync_events\` (
                \`id\` BIGINT NOT NULL AUTO_INCREMENT,
                \`user_id\` VARCHAR(64) NOT NULL,
                \`cursor\` BIGINT NOT NULL,
                \`event_type\` VARCHAR(32) NOT NULL DEFAULT 'message',
                \`message_id\` VARCHAR(64) NOT NULL,
                \`payload_json\` MEDIUMTEXT NOT NULL,
                \`created_at\` BIGINT NOT NULL,
                PRIMARY KEY (\`id\`),
                UNIQUE KEY \`uk_user_cursor\` (\`user_id\`, \`cursor\`),
                KEY \`idx_user_cursor_query\` (\`user_id\`, \`cursor\` ASC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createUserCursorsTable = `
            CREATE TABLE IF NOT EXISTS \`user_cursors\` (
                \`user_id\` VARCHAR(64) NOT NULL,
                \`max_cursor\` BIGINT NOT NULL DEFAULT 0,
                \`last_acked_cursor\` BIGINT NOT NULL DEFAULT 0,
                \`updated_at\` BIGINT NOT NULL,
                PRIMARY KEY (\`user_id\`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await this.pool.query(createSessionsTable);
        await this.pool.query(createMessagesTable);
        await this.pool.query(createSyncEventsTable);
        await this.pool.query(createUserCursorsTable);
        await this.pool.query("ALTER TABLE `messages` ADD COLUMN IF NOT EXISTS `updated_at` BIGINT NOT NULL DEFAULT 0");
        await this.pool.query("ALTER TABLE `messages` ADD COLUMN IF NOT EXISTS `recalled` TINYINT NOT NULL DEFAULT 0");
        await this.pool.query("ALTER TABLE `messages` ADD COLUMN IF NOT EXISTS `recalled_at` BIGINT NOT NULL DEFAULT 0");
        await this.pool.query("ALTER TABLE `messages` ADD COLUMN IF NOT EXISTS `recalled_by` VARCHAR(64) NOT NULL DEFAULT ''");
    }

    async findOrCreateSession(first, second) {
        const [userA, userB] = [first, second].sort();
        const [rows] = await this.pool.query(
            'SELECT `session_id` FROM `sessions` WHERE `user_a` = ? AND `user_b` = ? LIMIT 1',
            [userA, userB]
        );
        if (rows.length > 0) {
            return rows[0].session_id;
        }

        const sessionId = BaseStorage.generateP2PSessionId();
        const now = Date.now();
        try {
            await this.pool.query(
                'INSERT INTO `sessions` (`session_id`, `session_type`, `user_a`, `user_b`, `created_at`, `updated_at`) VALUES (?, 1, ?, ?, ?, ?)',
                [sessionId, userA, userB, now, now]
            );
            console.log(`[+] Created P2P session ${sessionId} for ${userA}:${userB}`);
            return sessionId;
        } catch (err) {
            // 处理并发冲突
            const [retryRows] = await this.pool.query(
                'SELECT `session_id` FROM `sessions` WHERE `user_a` = ? AND `user_b` = ? LIMIT 1',
                [userA, userB]
            );
            if (retryRows.length > 0) return retryRows[0].session_id;
            throw err;
        }
    }

    async _nextUserCursorInTx(conn, userId, now) {
        // 使用 FOR UPDATE 锁行保证游标严格递增
        const [rows] = await conn.query(
            'SELECT `max_cursor` FROM `user_cursors` WHERE `user_id` = ? FOR UPDATE',
            [userId]
        );
        let nextCursor = 1;
        if (rows.length > 0) {
            nextCursor = Number(rows[0].max_cursor) + 1;
            await conn.query(
                'UPDATE `user_cursors` SET `max_cursor` = ?, `updated_at` = ? WHERE `user_id` = ?',
                [nextCursor, now, userId]
            );
        } else {
            await conn.query(
                'INSERT INTO `user_cursors` (`user_id`, `max_cursor`, `last_acked_cursor`, `updated_at`) VALUES (?, 1, 0, ?)',
                [userId, now]
            );
        }
        return nextCursor;
    }

    async saveMessageAndEvents(senderId, receiverId, payload) {
        const sessionId = await this.findOrCreateSession(senderId, receiverId);
        const now = Date.now();
        const messageId = String(payload.messageId || '').trim();
        const sessionType = Number(payload.sessionType || 1);
        const msgType = Number(payload.type || 1);
        const attachment = typeof payload.attachment === 'string' ? payload.attachment : JSON.stringify(payload.attachment || {});
        const extra = typeof payload.extra === 'string' ? payload.extra : JSON.stringify(payload.extra || {});
        const status = Number(payload.status || 1);
        const sendTime = Number(payload.time || now);

        const fullMessage = {
            id: 0,
            messageId,
            sessionType,
            sessionId,
            senderId,
            receiverId,
            type: msgType,
            direct: 1,
            status,
            time: sendTime,
            attachment,
            extra
        };

        const conn = await this.pool.getConnection();
        try {
            await conn.beginTransaction();

            // 1. 插入消息中心
            await conn.query(
                `INSERT INTO \`messages\` 
                 (\`message_id\`, \`session_id\`, \`session_type\`, \`sender_id\`, \`receiver_id\`, \`msg_type\`, \`attachment\`, \`extra\`, \`status\`, \`send_time\`, \`created_at\`)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON DUPLICATE KEY UPDATE \`status\` = VALUES(\`status\`), \`updated_at\` = ?`,
                [messageId, sessionId, sessionType, senderId, receiverId, msgType, attachment, extra, status, sendTime, now, now]
            );

            // 2. 更新会话最新信息
            await conn.query(
                `UPDATE \`sessions\` 
                 SET \`last_message_id\` = ?, \`last_msg_preview\` = ?, \`last_msg_time\` = ?, \`updated_at\` = ?
                 WHERE \`session_id\` = ?`,
                [messageId, attachment.slice(0, 200), sendTime, now, sessionId]
            );

            // 3. 发送方分配游标并写入 sync_events
            const senderCursor = await this._nextUserCursorInTx(conn, senderId, now);
            await conn.query(
                `INSERT INTO \`sync_events\` (\`user_id\`, \`cursor\`, \`event_type\`, \`message_id\`, \`payload_json\`, \`created_at\`)
                 VALUES (?, ?, 'message', ?, ?, ?)`,
                [senderId, senderCursor, messageId, JSON.stringify(fullMessage), now]
            );

            // 4. 接收方分配游标并写入 sync_events
            const recipientCursor = await this._nextUserCursorInTx(conn, receiverId, now);
            await conn.query(
                `INSERT INTO \`sync_events\` (\`user_id\`, \`cursor\`, \`event_type\`, \`message_id\`, \`payload_json\`, \`created_at\`)
                 VALUES (?, ?, 'message', ?, ?, ?)`,
                [receiverId, recipientCursor, messageId, JSON.stringify(fullMessage), now]
            );

            await conn.commit();

            return {
                sessionId,
                fullMessage,
                senderCursor,
                recipientCursor,
                recipientEvent: {
                    cursor: recipientCursor,
                    eventType: 'message',
                    payload: fullMessage
                }
            };
        } catch (err) {
            await conn.rollback();
            throw err;
        } finally {
            conn.release();
        }
    }

    async recallMessage(userId, messageId, recallWindowMs) {
        const conn = await this.pool.getConnection();
        try {
            await conn.beginTransaction();
            const [rows] = await conn.query('SELECT * FROM `messages` WHERE `message_id` = ? FOR UPDATE', [messageId]);
            if (!rows.length) { await conn.rollback(); return { success: false, errorCode: 'NOT_FOUND', errorMessage: '消息不存在' }; }
            const row = rows[0];
            if (row.sender_id !== userId) { await conn.rollback(); return { success: false, errorCode: 'FORBIDDEN', errorMessage: '只能撤回自己发送的消息' }; }
            const now = Date.now();
            if (!row.recalled && now - Number(row.send_time) > recallWindowMs) {
                await conn.rollback(); return { success: false, errorCode: 'EXPIRED', errorMessage: '发送时间超过2分钟，不能撤回' };
            }
            if (!row.recalled) await conn.query('UPDATE `messages` SET `recalled`=1, `recalled_at`=?, `recalled_by`=? WHERE `message_id`=?', [now, userId, messageId]);
            const payload = { id: 0, messageId: row.message_id, sessionType: Number(row.session_type), sessionId: row.session_id,
                senderId: row.sender_id, receiverId: row.receiver_id, type: Number(row.msg_type), direct: 1,
                status: 1, time: Number(row.send_time), attachment: row.attachment, extra: row.extra,
                recalled: true, recalledAt: Number(row.recalled_at || now), recalledBy: row.recalled_by || userId };
            if (!row.recalled) {
                for (const target of [row.sender_id, row.receiver_id]) {
                    const cursor = await this._nextUserCursorInTx(conn, target, now);
                    await conn.query("INSERT INTO `sync_events` (`user_id`,`cursor`,`event_type`,`message_id`,`payload_json`,`created_at`) VALUES (?, ?, 'recall', ?, ?, ?)", [target, cursor, messageId, JSON.stringify(payload), now]);
                }
                await conn.query("UPDATE `sessions` SET `last_msg_preview`='撤回了一条消息', `updated_at`=? WHERE `session_id`=? AND `last_message_id`=?", [now, row.session_id, messageId]);
            }
            await conn.commit();
            return { success: true, message: payload };
        } catch (err) { await conn.rollback(); throw err; } finally { conn.release(); }
    }

    async getSyncEvents(userId, cursor, limit) {
        const pageSize = Math.max(1, limit || this.config.syncPageSize || 100);
        const startCursor = Math.max(0, Number(cursor || 0));
        const [cursorRows] = await this.pool.query(
            'SELECT `max_cursor` AS `maxCursor`, `last_acked_cursor` AS `lastAckedCursor` FROM `user_cursors` WHERE `user_id` = ?',
            [userId]
        );
        console.log(`[Sync][MySQL] query user=${userId} requested=${startCursor} max=${cursorRows[0]?.maxCursor ?? 0} lastAck=${cursorRows[0]?.lastAckedCursor ?? 0}`);

        const [rows] = await this.pool.query(
            `SELECT \`cursor\`, \`event_type\` AS \`eventType\`, \`payload_json\` 
             FROM \`sync_events\` 
             WHERE \`user_id\` = ? AND \`cursor\` > ? 
             ORDER BY \`cursor\` ASC 
             LIMIT ?`,
            [userId, startCursor, pageSize]
        );

        const events = rows.map(r => ({
            cursor: Number(r.cursor),
            eventType: r.eventType,
            payload: JSON.parse(r.payload_json)
        }));

        const nextCursor = events.length ? events[events.length - 1].cursor : startCursor;

        const [countRows] = await this.pool.query(
            `SELECT COUNT(1) AS \`count\` 
             FROM \`sync_events\` 
             WHERE \`user_id\` = ? AND \`cursor\` > ?`,
            [userId, nextCursor]
        );
        const hasMore = (countRows[0] ? Number(countRows[0].count) : 0) > 0;

        return {
            events,
            nextCursor,
            hasMore
        };
    }

    async updateUserSyncAck(userId, cursor) {
        const ackCursor = Math.max(0, Number(cursor || 0));
        const now = Date.now();
        await this.pool.query(
            `INSERT INTO \`user_cursors\` (\`user_id\`, \`max_cursor\`, \`last_acked_cursor\`, \`updated_at\`)
             VALUES (?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE 
                \`last_acked_cursor\` = GREATEST(\`last_acked_cursor\`, VALUES(\`last_acked_cursor\`)),
                \`updated_at\` = VALUES(\`updated_at\`)`,
            [userId, ackCursor, ackCursor, now]
        );
    }

    async close() {
        if (this.pool) {
            await this.pool.end();
            this.pool = null;
        }
    }
}

module.exports = MysqlStorage;
