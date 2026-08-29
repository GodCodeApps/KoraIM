// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const crypto = require('crypto');

class BaseStorage {
    /**
     * 根据双方账号生成唯一的双人排序 key，保证 A->B 和 B->A 映射到同一会话
     */
    static p2pKey(first, second) {
        return [first, second].sort().join(':');
    }

    /**
     * 生成 P2P 会话 ID
     */
    static generateP2PSessionId() {
        return `p2p_${crypto.randomUUID()}`;
    }

    async init() {
        throw new Error('init() not implemented');
    }

    async findOrCreateSession(first, second) {
        throw new Error('findOrCreateSession() not implemented');
    }

    async saveMessageAndEvents(senderId, receiverId, payload) {
        throw new Error('saveMessageAndEvents() not implemented');
    }

    async getSyncEvents(userId, cursor, limit) {
        throw new Error('getSyncEvents() not implemented');
    }

    async recallMessage(userId, messageId, recallWindowMs) {
        throw new Error('recallMessage() not implemented');
    }

    async updateUserSyncAck(userId, cursor) {
        throw new Error('updateUserSyncAck() not implemented');
    }

    async close() {
        // Optional
    }
}

module.exports = BaseStorage;
