// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const assert = require('assert');

const PORT = 8090;

function createClient(account) {
    const client = new net.Socket();
    client.setEncoding('utf8');
    const incomingQueue = [];
    let buffer = '';

    client.on('data', chunk => {
        buffer += chunk;
        let idx;
        while ((idx = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, idx).trim();
            buffer = buffer.slice(idx + 1);
            if (line) {
                const parsed = JSON.parse(line);
                incomingQueue.push(parsed);
                if (client.onMessage) client.onMessage(parsed);
            }
        }
    });

    return {
        socket: client,
        account,
        connect() {
            return new Promise((resolve, reject) => {
                client.connect(PORT, '127.0.0.1', () => {
                    this.send({ type: 'login', account });
                    setTimeout(resolve, 100);
                });
                client.on('error', reject);
            });
        },
        send(frame) {
            client.write(`${JSON.stringify(frame)}\n`);
        },
        waitFor(predicate, timeout = 3000) {
            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => {
                    reject(new Error(`Timeout waiting for frame matching condition`));
                }, timeout);

                const check = () => {
                    const idx = incomingQueue.findIndex(predicate);
                    if (idx >= 0) {
                        clearTimeout(timer);
                        const [item] = incomingQueue.splice(idx, 1);
                        resolve(item);
                    }
                };

                check();
                const orig = client.onMessage;
                client.onMessage = (msg) => {
                    if (orig) orig(msg);
                    check();
                };
            });
        },
        close() {
            client.destroy();
        }
    };
}

async function runTests() {
    console.log('=== Starting IM Server Persistence & Sync Tests ===\n');

    // 1. 测试 Alice 登录并向离线的 Bob 发送消息
    console.log('[Step 1] Alice connects and sends an offline message to Bob...');
    const alice = createClient('user_alice');
    await alice.connect();

    const msg1Id = `msg_${Date.now()}_1`;
    alice.send({
        type: 'message',
        messageId: msg1Id,
        payload: {
            messageId: msg1Id,
            sessionType: 1,
            sessionId: '',
            receiverId: 'user_bob',
            type: 1,
            attachment: JSON.stringify({ text: 'Hello Bob! This is an offline test.' }),
            extra: '',
            status: 1,
            time: Date.now()
        }
    });

    const ack1 = await alice.waitFor(frame => frame.type === 'ack' && frame.messageId === msg1Id);
    console.log('  -> Alice received ACK:', ack1);
    assert.strictEqual(ack1.success, true);
    assert.ok(ack1.sessionId.startsWith('p2p_'));

    // 2. Bob 登录并发起 sync(0) 拉取离线消息
    console.log('\n[Step 2] Bob connects and syncs offline messages (cursor=0)...');
    const bob = createClient('user_bob');
    await bob.connect();

    bob.send({ type: 'sync', cursor: 0 });
    const syncRes = await bob.waitFor(frame => frame.type === 'sync_result');
    console.log('  -> Bob received sync_result:', JSON.stringify(syncRes, null, 2));
    assert.ok(syncRes.events.length >= 1, 'Bob should receive at least 1 offline event');
    const receivedEvent = syncRes.events.find(e => e.payload && e.payload.messageId === msg1Id);
    assert.ok(receivedEvent, 'Offline event must contain Alice\'s message');
    assert.strictEqual(receivedEvent.payload.senderId, 'user_alice');

    // 3. Bob 回送 sync_ack
    console.log('\n[Step 3] Bob sends sync_ack...');
    bob.send({ type: 'sync_ack', cursor: syncRes.nextCursor });

    // 4. Bob 在线时，Alice 再次发送消息，验证实时在线推送
    console.log('\n[Step 4] Alice sends a live message to online Bob...');
    const msg2Id = `msg_${Date.now()}_2`;
    alice.send({
        type: 'message',
        messageId: msg2Id,
        payload: {
            messageId: msg2Id,
            sessionType: 1,
            sessionId: ack1.sessionId,
            receiverId: 'user_bob',
            type: 1,
            attachment: JSON.stringify({ text: 'Hey Bob, are you online?' }),
            extra: '',
            status: 1,
            time: Date.now()
        }
    });

    const liveMsg = await bob.waitFor(frame => frame.type === 'message' && frame.messageId === msg2Id);
    console.log('  -> Bob received real-time pushed message:', liveMsg);
    assert.strictEqual(liveMsg.messageId, msg2Id);
    assert.strictEqual(liveMsg.payload.senderId, 'user_alice');

    // 5. 验证 Typing Indicator 实时信令透传
    console.log('\n[Step 5] Alice sends typing indicator to Bob...');
    alice.send({ type: 'typing', receiverId: 'user_bob' });
    const typingFrame = await bob.waitFor(frame => frame.type === 'typing' && frame.senderId === 'user_alice');
    console.log('  -> Bob received typing event:', typingFrame);
    assert.strictEqual(typingFrame.senderId, 'user_alice');

    alice.close();
    bob.close();

    console.log('\n=== All Tests Passed Successfully! ===');
    process.exit(0);
}

runTests().catch(err => {
    console.error('[!] Test failed:', err);
    process.exit(1);
});
