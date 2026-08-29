// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const BaseStorage = require('./base_storage');

class SqliteStorage extends BaseStorage {
    constructor(config) {
        super();
        this.config = config;
        this.db = null;
    }

    async init() {
        const dbPath = path.resolve(process.cwd(), this.config.sqlite.filename);
        const dir = path.dirname(dbPath);
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }

        this.db = new Database(dbPath);
        this.db.pragma('journal_mode = WAL');
        this.db.pragma('synchronous = NORMAL');
        this.db.pragma('foreign_keys = ON');

        this._createTables();
        this._prepareStatements();
        console.log(`[+] SQLite database initialized: ${dbPath}`);
    }

    _createTables() {
        this.db.exec(`
            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY,
                session_type INTEGER NOT NULL DEFAULT 1,
                user_a TEXT NOT NULL,
                user_b TEXT NOT NULL,
                last_message_id TEXT NOT NULL DEFAULT '',
                last_msg_preview TEXT NOT NULL DEFAULT '',
                last_msg_time INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(user_a, user_b)
            );

            CREATE TABLE IF NOT EXISTS messages (
                message_id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                session_type INTEGER NOT NULL DEFAULT 1,
                sender_id TEXT NOT NULL,
                receiver_id TEXT NOT NULL,
                msg_type INTEGER NOT NULL DEFAULT 1,
                attachment TEXT NOT NULL DEFAULT '',
                extra TEXT NOT NULL DEFAULT '',
                status INTEGER NOT NULL DEFAULT 1,
                send_time INTEGER NOT NULL,
                created_at INTEGER NOT NULL
                ,recalled INTEGER NOT NULL DEFAULT 0
                ,recalled_at INTEGER NOT NULL DEFAULT 0
                ,recalled_by TEXT NOT NULL DEFAULT ''
            );

            CREATE INDEX IF NOT EXISTS idx_messages_session_time 
                ON messages(session_id, send_time DESC);

            CREATE TABLE IF NOT EXISTS sync_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                cursor INTEGER NOT NULL,
                event_type TEXT NOT NULL DEFAULT 'message',
                message_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(user_id, cursor)
            );

            CREATE INDEX IF NOT EXISTS idx_sync_events_user_cursor 
                ON sync_events(user_id, cursor ASC);

            CREATE TABLE IF NOT EXISTS user_cursors (
                user_id TEXT PRIMARY KEY,
                max_cursor INTEGER NOT NULL DEFAULT 0,
                last_acked_cursor INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            );
        `);
        const messageColumns = new Set(this.db.prepare('PRAGMA table_info(messages)').all().map(c => c.name));
        if (!messageColumns.has('recalled')) this.db.exec(`ALTER TABLE messages ADD COLUMN recalled INTEGER NOT NULL DEFAULT 0`);
        if (!messageColumns.has('recalled_at')) this.db.exec(`ALTER TABLE messages ADD COLUMN recalled_at INTEGER NOT NULL DEFAULT 0`);
        if (!messageColumns.has('recalled_by')) this.db.exec(`ALTER TABLE messages ADD COLUMN recalled_by TEXT NOT NULL DEFAULT ''`);
    }

    _prepareStatements() {
        this.stmtGetSession = this.db.prepare(
            `SELECT session_id FROM sessions WHERE user_a = ? AND user_b = ?`
        );
        this.stmtInsertSession = this.db.prepare(
            `INSERT INTO sessions (session_id, session_type, user_a, user_b, created_at, updated_at) 
             VALUES (?, 1, ?, ?, ?, ?)`
        );
        this.stmtUpdateSessionLastMsg = this.db.prepare(
            `UPDATE sessions 
             SET last_message_id = ?, last_msg_preview = ?, last_msg_time = ?, updated_at = ? 
             WHERE session_id = ?`
        );
        this.stmtInsertMessage = this.db.prepare(
            `INSERT OR REPLACE INTO messages 
             (message_id, session_id, session_type, sender_id, receiver_id, msg_type, attachment, extra, status, send_time, created_at) 
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        );
        this.stmtGetUserCursor = this.db.prepare(
            `SELECT max_cursor FROM user_cursors WHERE user_id = ?`
        );
        this.stmtUpsertUserCursor = this.db.prepare(
            `INSERT INTO user_cursors (user_id, max_cursor, last_acked_cursor, updated_at) 
             VALUES (?, ?, 0, ?) 
             ON CONFLICT(user_id) DO UPDATE SET max_cursor = ?, updated_at = ?`
        );
        this.stmtInsertSyncEvent = this.db.prepare(
            `INSERT INTO sync_events (user_id, cursor, event_type, message_id, payload_json, created_at) 
             VALUES (?, ?, ?, ?, ?, ?)`
        );
        this.stmtGetSyncEvents = this.db.prepare(
            `SELECT cursor, event_type AS eventType, payload_json 
             FROM sync_events 
             WHERE user_id = ? AND cursor > ? 
             ORDER BY cursor ASC 
             LIMIT ?`
        );
        this.stmtCountMoreSyncEvents = this.db.prepare(
            `SELECT COUNT(1) AS count 
             FROM sync_events 
             WHERE user_id = ? AND cursor > ?`
        );
        this.stmtUpdateUserAck = this.db.prepare(
            `INSERT INTO user_cursors (user_id, max_cursor, last_acked_cursor, updated_at) 
             VALUES (?, ?, ?, ?) 
             ON CONFLICT(user_id) DO UPDATE SET last_acked_cursor = MAX(last_acked_cursor, ?), updated_at = ?`
        );
        this.stmtGetMessage = this.db.prepare(`SELECT * FROM messages WHERE message_id = ?`);
        this.stmtRecallMessage = this.db.prepare(
            `UPDATE messages SET recalled = 1, recalled_at = ?, recalled_by = ? WHERE message_id = ?`
        );
    }

    async findOrCreateSession(first, second) {
        const [userA, userB] = [first, second].sort();
        const existing = this.stmtGetSession.get(userA, userB);
        if (existing) {
            return existing.session_id;
        }
        const sessionId = BaseStorage.generateP2PSessionId();
        const now = Date.now();
        try {
            this.stmtInsertSession.run(sessionId, userA, userB, now, now);
            console.log(`[+] Created P2P session ${sessionId} for ${userA}:${userB}`);
            return sessionId;
        } catch (err) {
            // 针对并发冲突重查一次
            const retry = this.stmtGetSession.get(userA, userB);
            if (retry) return retry.session_id;
            throw err;
        }
    }

    _nextUserCursor(userId, now) {
        const row = this.stmtGetUserCursor.get(userId);
        const nextCursor = (row ? row.max_cursor : 0) + 1;
        this.stmtUpsertUserCursor.run(userId, nextCursor, now, nextCursor, now);
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

        const tx = this.db.transaction(() => {
            // 1. 保存消息主体
            this.stmtInsertMessage.run(
                messageId,
                sessionId,
                sessionType,
                senderId,
                receiverId,
                msgType,
                attachment,
                extra,
                status,
                sendTime,
                now
            );

            // 2. 更新会话最新信息
            this.stmtUpdateSessionLastMsg.run(
                messageId,
                attachment.slice(0, 200),
                sendTime,
                now,
                sessionId
            );

            // 3. 发送方分配游标并写入 sync_events
            const senderCursor = this._nextUserCursor(senderId, now);
            this.stmtInsertSyncEvent.run(
                senderId,
                senderCursor,
                'message',
                messageId,
                JSON.stringify(fullMessage),
                now
            );

            // 4. 接收方分配游标并写入 sync_events
            const recipientCursor = this._nextUserCursor(receiverId, now);
            this.stmtInsertSyncEvent.run(
                receiverId,
                recipientCursor,
                'message',
                messageId,
                JSON.stringify(fullMessage),
                now
            );

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
        });

        return tx();
    }

    async getSyncEvents(userId, cursor, limit) {
        const pageSize = Math.max(1, limit || this.config.syncPageSize || 100);
        const startCursor = Math.max(0, Number(cursor || 0));
        const cursorState = this.db.prepare(
            `SELECT max_cursor AS maxCursor, last_acked_cursor AS lastAckedCursor FROM user_cursors WHERE user_id = ?`
        ).get(userId);
        console.log(`[Sync][SQLite] query user=${userId} requested=${startCursor} max=${cursorState?.maxCursor ?? 0} lastAck=${cursorState?.lastAckedCursor ?? 0}`);

        const rows = this.stmtGetSyncEvents.all(userId, startCursor, pageSize);
        const events = rows.map(r => ({
            cursor: Number(r.cursor),
            eventType: r.eventType,
            payload: JSON.parse(r.payload_json)
        }));

        const nextCursor = events.length ? events[events.length - 1].cursor : startCursor;
        const countRow = this.stmtCountMoreSyncEvents.get(userId, nextCursor);
        const hasMore = (countRow ? countRow.count : 0) > 0;

        return {
            events,
            nextCursor,
            hasMore
        };
    }

    async recallMessage(userId, messageId, recallWindowMs) {
        const row = this.stmtGetMessage.get(messageId);
        if (!row) return { success: false, errorCode: 'NOT_FOUND', errorMessage: '消息不存在' };
        if (row.sender_id !== userId) return { success: false, errorCode: 'FORBIDDEN', errorMessage: '只能撤回自己发送的消息' };
        if (row.recalled) return { success: true, message: this._recalledPayload(row) };
        const now = Date.now();
        if (now - Number(row.send_time) > recallWindowMs) {
            return { success: false, errorCode: 'EXPIRED', errorMessage: '发送时间超过2分钟，不能撤回' };
        }
        const tx = this.db.transaction(() => {
            this.stmtRecallMessage.run(now, userId, messageId);
            const payload = this._recalledPayload({ ...row, recalled: 1, recalled_at: now, recalled_by: userId });
            for (const target of [row.sender_id, row.receiver_id]) {
                const cursor = this._nextUserCursor(target, now);
                this.stmtInsertSyncEvent.run(target, cursor, 'recall', messageId, JSON.stringify(payload), now);
            }
            if (this.db.prepare(`SELECT last_message_id FROM sessions WHERE session_id = ?`).get(row.session_id)?.last_message_id === messageId) {
                this.stmtUpdateSessionLastMsg.run(messageId, '撤回了一条消息', Number(row.send_time), now, row.session_id);
            }
            return payload;
        });
        return { success: true, message: tx() };
    }

    _recalledPayload(row) {
        return { id: 0, messageId: row.message_id, sessionType: Number(row.session_type),
            sessionId: row.session_id, senderId: row.sender_id, receiverId: row.receiver_id,
            type: Number(row.msg_type), direct: 1, status: 1, time: Number(row.send_time),
            attachment: row.attachment, extra: row.extra, recalled: true,
            recalledAt: Number(row.recalled_at || 0), recalledBy: row.recalled_by || row.sender_id };
    }

    async updateUserSyncAck(userId, cursor) {
        const ackCursor = Math.max(0, Number(cursor || 0));
        const now = Date.now();
        this.stmtUpdateUserAck.run(userId, ackCursor, ackCursor, now, ackCursor, now);
    }

    async close() {
        if (this.db) {
            this.db.close();
            this.db = null;
        }
    }
}

module.exports = SqliteStorage;
