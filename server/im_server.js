// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const crypto = require('crypto');
const PORT = Number(process.env.PORT || 8090);
const clients = new Map();
const p2pSessions = new Map();
const userEvents = new Map();
const userCursors = new Map();
const SYNC_PAGE_SIZE = 100;

function p2pKey(first, second) {
    return [first, second].sort().join(':');
}

function findOrCreateP2PSession(first, second) {
    const key = p2pKey(first, second);
    let sessionId = p2pSessions.get(key);
    if (!sessionId) {
        sessionId = `p2p_${crypto.randomUUID()}`;
        p2pSessions.set(key, sessionId);
        console.log(`[+] Created P2P session ${sessionId} for ${key}`);
    }
    return sessionId;
}

function appendUserEvent(userId, payload) {
    const cursor = (userCursors.get(userId) || 0) + 1;
    userCursors.set(userId, cursor);
    const event = { cursor, eventType: 'message', payload };
    const events = userEvents.get(userId) || [];
    events.push(event);
    userEvents.set(userId, events);
    return event;
}

function writeFrame(socket, frame) {
    socket.write(`${JSON.stringify(frame)}\n`);
}

const server = net.createServer((socket) => {
    console.log(`[+] Client connected: ${socket.remoteAddress}:${socket.remotePort}`);
    socket.setEncoding('utf8');
    let buffer = '';

    socket.on('data', (chunk) => {
        buffer += chunk;
        let newlineIndex;
        while ((newlineIndex = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, newlineIndex).trim();
            buffer = buffer.slice(newlineIndex + 1);
            if (line) handleFrame(socket, line);
        }
    });

    socket.on('end', () => removeClient(socket));
    socket.on('close', () => removeClient(socket));
    socket.on('error', (error) => console.error(`[x] Socket error: ${error.message}`));
});

function handleFrame(socket, line) {
    try {
        const envelope = JSON.parse(line);
        if (envelope.type === 'login') {
            const account = String(envelope.account || '').trim();
            if (!account) throw new Error('Account is required');
            const previous = clients.get(account);
            if (previous && previous !== socket) previous.end();
            socket.account = account;
            clients.set(account, socket);
            console.log(`[=] ${account} logged in`);
            return;
        }
        // The Android client acknowledges delivered incoming messages. Delivery
        // receipts are intentionally not persisted by this minimal demo server.
        if (envelope.type === 'ack') return;
        if (envelope.type === 'sync_ack') return;
        if (envelope.type === 'sync') {
            if (!socket.account) throw new Error('Login required');
            const cursor = Math.max(0, Number(envelope.cursor || 0));
            const pending = (userEvents.get(socket.account) || []).filter(event => event.cursor > cursor);
            const events = pending.slice(0, SYNC_PAGE_SIZE);
            const nextCursor = events.length ? events[events.length - 1].cursor : cursor;
            writeFrame(socket, {
                type: 'sync_result',
                events,
                nextCursor,
                hasMore: pending.length > events.length
            });
            return;
        }
        if (envelope.type !== 'message' || !envelope.payload || !envelope.messageId) {
            throw new Error('Unsupported frame');
        }
        if (!socket.account) throw new Error('Login required');

        const recipientId = String(envelope.payload.receiverId || '').trim();
        if (!recipientId || recipientId === socket.account) throw new Error('Invalid receiverId');
        const sessionId = findOrCreateP2PSession(socket.account, recipientId);

        const incoming = {
            ...envelope.payload,
            id: 0,
            sessionId,
            senderId: socket.account,
            receiverId: recipientId,
            direct: 1,
            status: 1,
            time: Date.now()
        };
        appendUserEvent(socket.account, incoming);
        const event = appendUserEvent(recipientId, incoming);
        writeFrame(socket, { type: 'ack', messageId: envelope.messageId, success: true, sessionId });
        const recipient = clients.get(recipientId);
        if (recipient && !recipient.destroyed) {
            writeFrame(recipient, {
                type: 'message',
                messageId: incoming.messageId,
                cursor: event.cursor,
                payload: incoming
            });
            console.log(`[>] ${socket.account} -> ${recipientId}`);
        } else {
            console.log(`[>] ${socket.account} -> ${recipientId} (stored offline)`);
        }
    } catch (error) {
        console.error(`[!] Invalid frame: ${error.message}`);
    }
}

function removeClient(socket) {
    if (socket.account && clients.get(socket.account) === socket) {
        clients.delete(socket.account);
        console.log(`[-] ${socket.account} disconnected`);
    }
}

server.on('error', (error) => console.error(`[x] Server error: ${error.message}`));
server.listen(PORT, () => console.log(`KoraIM test server listening on ${PORT}`));
