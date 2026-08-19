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
