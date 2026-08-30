// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const crypto = require('crypto');
const config = require('./config');
const { createStorage } = require('./storage');

const PORT = config.port;
const clients = new Map();
const activeCalls = new Map();
const userCalls = new Map();
const storage = createStorage(config);

function releaseCall(callId) {
    const call = activeCalls.get(callId);
    if (!call) return;
    activeCalls.delete(callId);
    if (userCalls.get(call.caller) === callId) userCalls.delete(call.caller);
    if (userCalls.get(call.callee) === callId) userCalls.delete(call.callee);
    if (call.timer) clearTimeout(call.timer);
}

async function persistCallRecord(callId, call, action, actorId) {
    if (!call || call.recorded) return;
    call.recorded = true;
    const now = Date.now();
    const completed = action === 'hangup' && Boolean(call.acceptedAt);
    const result = completed ? 'completed' : ({
        reject: 'rejected', cancel: 'cancelled', timeout: 'missed', busy: 'busy'
    }[action] || 'interrupted');
    const attachment = JSON.stringify({
        callId,
        callType: call.callType || 'audio',
        result,
        callerId: call.caller,
        calleeId: call.callee,
        actorId,
        durationSeconds: completed ? Math.max(0, Math.floor((now - call.acceptedAt) / 1000)) : 0,
        endedAt: now
    });
    const saved = await storage.saveMessageAndEvents(call.caller, call.callee, {
        messageId: crypto.randomUUID(), sessionType: 1, type: 10,
        attachment, extra: '', status: 1, time: now
    });
    for (const account of [call.caller, call.callee]) {
        clients.get(account)?.forEach(client => writeFrame(client, {
            type: 'message', messageId: saved.fullMessage.messageId, payload: saved.fullMessage
        }));
    }
}

function writeFrame(socket, frame) {
    if (!socket.destroyed) {
        socket.write(`${JSON.stringify(frame)}\n`);
    }
}

async function handleFrame(socket, line) {
    try {
        const envelope = JSON.parse(line);

        // 1. 处理心跳
        if (envelope.type === 'ping') {
            writeFrame(socket, { type: 'pong' });
            return;
        }

        // 2. 登录认证
        if (envelope.type === 'login') {
            const account = String(envelope.account || '').trim();
            if (!account) throw new Error('Account is required');
            socket.account = account;
            const accountSockets = clients.get(account) || new Set();
            accountSockets.add(socket);
            clients.set(account, accountSockets);
            console.log(`[=] ${account} logged in`);
            return;
        }

        // 3. 消息投递 ACK
        if (envelope.type === 'ack') {
            return;
        }

        // 3.1 正在输入状态透传 (Typing Indicator)
        if (envelope.type === 'typing') {
            if (!socket.account) return;
            const recipientId = String(envelope.receiverId || '').trim();
            if (recipientId && recipientId !== socket.account) {
                const recipientSockets = clients.get(recipientId);
                const count = recipientSockets?.size || 0;
                console.log(`[Typing] ${socket.account} is typing to ${recipientId} (online targets: ${count})`);
                recipientSockets?.forEach(recSocket => {
                    if (!recSocket.destroyed) {
                        writeFrame(recSocket, {
                            type: 'typing',
                            senderId: socket.account
                        });
                    }
                });
            }
            return;
        }

        // 4. 同步 ACK（客户端确认消费到的最新游标）
        if (envelope.type === 'sync_ack') {
            if (socket.account && envelope.cursor !== undefined) {
                console.log(`[Sync] ack account=${socket.account} cursor=${Number(envelope.cursor)}`);
                await storage.updateUserSyncAck(socket.account, envelope.cursor);
            }
            return;
        }

        // 5. 增量游标同步 (Sync)
        if (envelope.type === 'sync') {
            if (!socket.account) throw new Error('Login required');
            const cursor = Math.max(0, Number(envelope.cursor || 0));
            console.log(`[Sync] request account=${socket.account} cursor=${cursor}`);
            const { events, nextCursor, hasMore } = await storage.getSyncEvents(
                socket.account,
                cursor,
                config.syncPageSize
            );
            console.log(
                `[Sync] result account=${socket.account} requested=${cursor} events=${events.length} ` +
                `nextCursor=${nextCursor} hasMore=${hasMore} items=` +
                events.slice(0, 20).map(event => `${event.eventType}:${event.payload?.messageId || ''}@${event.cursor}`).join(',')
            );
            writeFrame(socket, {
                type: 'sync_result',
                events,
                nextCursor,
                hasMore
            });
            return;
        }

        if (envelope.type === 'recall') {
            if (!socket.account) throw new Error('Login required');
            const requestId = String(envelope.requestId || '');
            const messageId = String(envelope.messageId || '');
            const result = await storage.recallMessage(socket.account, messageId, 2 * 60 * 1000);
            writeFrame(socket, { type: 'recall_ack', requestId, messageId, success: result.success,
                errorCode: result.errorCode || '', errorMessage: result.errorMessage || '' });
            if (result.success && result.message) {
                for (const target of new Set([result.message.senderId, result.message.receiverId])) {
                    clients.get(target)?.forEach(client => writeFrame(client, {
                        type: 'recall', messageId, payload: result.message
                    }));
                }
            }
            return;
        }

        if (envelope.type === 'call_signal') {
            if (!socket.account) throw new Error('Login required');
            const signal = envelope.callSignal || {};
            const receiverId = String(signal.receiverId || '').trim();
            const callId = String(signal.callId || '').trim();
            const action = String(signal.action || '').trim();
            if (!receiverId || !callId || !action || receiverId === socket.account) {
                throw new Error('Invalid call signal');
            }
            const routed = { ...signal, senderId: socket.account, receiverId, callId, action, timestamp: Date.now() };
            if (action === 'invite') {
                if (userCalls.has(socket.account) || userCalls.has(receiverId)) {
                    writeFrame(socket, { type: 'call_signal', callSignal: {
                        ...routed, action: 'busy', senderId: receiverId, receiverId: socket.account
                    }});
                    await persistCallRecord(callId, {
                        caller: socket.account, callee: receiverId, callType: signal.callType || 'audio', recorded: false
                    }, 'busy', receiverId);
                    return;
                }
                const call = { caller: socket.account, callee: receiverId, callType: signal.callType || 'audio', startedAt: Date.now(), acceptedAt: 0, timer: null, recorded: false };
                call.timer = setTimeout(() => releaseCall(callId), 60000);
                activeCalls.set(callId, call);
                userCalls.set(socket.account, callId);
                userCalls.set(receiverId, callId);
            } else {
                const call = activeCalls.get(callId);
                if (!call || ![call.caller, call.callee].includes(socket.account) || ![call.caller, call.callee].includes(receiverId)) {
                    throw new Error('Invalid or expired call');
                }
                if (action === 'accept' && !call.acceptedAt) call.acceptedAt = Date.now();
            }
            const targets = clients.get(receiverId);
            const activeTargets = targets ? [...targets].filter(client => !client.destroyed) : [];
            if (!activeTargets.length && action === 'invite') {
                writeFrame(socket, { type: 'call_signal', callSignal: {
                    ...routed, action: 'busy', senderId: receiverId, receiverId: socket.account,
                    payload: JSON.stringify({ reason: 'offline' })
                }});
                await persistCallRecord(callId, activeCalls.get(callId), 'busy', receiverId);
                releaseCall(callId);
                return;
            }
            activeTargets.forEach(client => writeFrame(client, { type: 'call_signal', callSignal: routed }));
            if (['reject', 'cancel', 'hangup', 'busy', 'timeout'].includes(action)) {
                const call = activeCalls.get(callId);
                await persistCallRecord(callId, call, action, socket.account);
                releaseCall(callId);
            }
            return;
        }

        // 6. 发送即时消息
        if (envelope.type !== 'message' || !envelope.payload || !envelope.messageId) {
            throw new Error('Unsupported frame');
        }
        if (!socket.account) throw new Error('Login required');

        const recipientId = String(envelope.payload.receiverId || '').trim();
        if (!recipientId || recipientId === socket.account) {
            throw new Error('Invalid receiverId');
        }

        // 原子持久化：存消息、更新会话、分配游标、生成双方同步事件
        const result = await storage.saveMessageAndEvents(socket.account, recipientId, envelope.payload);

        // 回复发送方 ACK
        writeFrame(socket, {
            type: 'ack',
            messageId: envelope.messageId,
            success: true,
            sessionId: result.sessionId
        });

        // 检查接收方在线连接并路由推送
        const recipientSockets = clients.get(recipientId);
        const activeRecipients = recipientSockets
            ? [...recipientSockets].filter(recipient => !recipient.destroyed)
            : [];

        if (activeRecipients.length) {
            activeRecipients.forEach(recipient => writeFrame(recipient, {
                type: 'message',
                messageId: result.fullMessage.messageId,
                cursor: result.recipientCursor,
                payload: result.fullMessage
            }));
            console.log(`[>] ${socket.account} -> ${recipientId} (delivered online)`);
        } else {
            console.log(`[>] ${socket.account} -> ${recipientId} (stored offline, cursor: ${result.recipientCursor})`);
        }
    } catch (error) {
        console.error(`[!] Frame handling error: ${error.message}`);
    }
}

async function removeClient(socket) {
    if (socket.account) {
        const accountSockets = clients.get(socket.account);
        accountSockets?.delete(socket);
        if (accountSockets?.size === 0) {
            clients.delete(socket.account);
            const callId = userCalls.get(socket.account);
            const call = callId ? activeCalls.get(callId) : null;
            if (call) {
                const peerId = call.caller === socket.account ? call.callee : call.caller;
                clients.get(peerId)?.forEach(client => writeFrame(client, { type: 'call_signal', callSignal: {
                    callId, action: 'hangup', senderId: socket.account, receiverId: peerId,
                    callType: '', payload: JSON.stringify({ reason: 'disconnected' }), timestamp: Date.now()
                }}));
                await persistCallRecord(callId, call, 'disconnected', socket.account);
                releaseCall(callId);
            }
        }
        console.log(`[-] ${socket.account} disconnected`);
    }
}

async function startServer() {
    // 1. 初始化持久化存储
    await storage.init();

    // 2. 创建 TCP 服务
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

    server.on('error', (error) => console.error(`[x] Server error: ${error.message}`));

    server.listen(PORT, () => {
        console.log(`KoraIM Server running on port ${PORT} [Storage Engine: ${config.dbType.toUpperCase()}]`);
    });

    const shutdown = async () => {
        console.log('\n[*] Shutting down server...');
        server.close(async () => {
            await storage.close();
            console.log('[*] Server shut down gracefully');
            process.exit(0);
        });
    };

    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
}

startServer().catch(err => {
    console.error(`[FATAL] Failed to start server: ${err.message}`, err);
    process.exit(1);
});
