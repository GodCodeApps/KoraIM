const net = require('net');
const assert = require('assert');
const { spawn } = require('child_process');
const path = require('path');

const PORT = 8090;

async function ensureServerRunning() {
    return new Promise((resolve) => {
        const testSocket = new net.Socket();
        testSocket.connect(PORT, '127.0.0.1', () => {
            testSocket.destroy();
            resolve(null); // Already running
        });
        testSocket.on('error', () => {
            console.log('[*] Server not running, starting im_server.js...');
            const srv = spawn('node', [path.join(__dirname, '../im_server.js')], {
                stdio: 'inherit'
            });
            setTimeout(() => resolve(srv), 1500);
        });
    });
}

function createSocket() {
    const socket = new net.Socket();
    socket.setEncoding('utf8');
    const messages = [];
    let buffer = '';

    socket.on('data', chunk => {
        buffer += chunk;
        let idx;
        while ((idx = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, idx).trim();
            buffer = buffer.slice(idx + 1);
            if (line) {
                messages.push(JSON.parse(line));
                if (socket.onMessage) socket.onMessage(JSON.parse(line));
            }
        }
    });

    return {
        socket,
        messages,
        connect(account) {
            return new Promise((resolve, reject) => {
                socket.connect(PORT, '127.0.0.1', () => {
                    socket.write(JSON.stringify({ type: 'login', account }) + '\n');
                    setTimeout(resolve, 100);
                });
                socket.on('error', reject);
            });
        },
        waitFor(predicate, timeout = 3000) {
            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => {
                    reject(new Error('Timeout waiting for message'));
                }, timeout);
                const check = () => {
                    const idx = messages.findIndex(predicate);
                    if (idx >= 0) {
                        clearTimeout(timer);
                        const [found] = messages.splice(idx, 1);
                        resolve(found);
                    }
                };
                check();
                const orig = socket.onMessage;
                socket.onMessage = (msg) => {
                    if (orig) orig(msg);
                    check();
                };
            });
        }
    };
}

async function testKick() {
    console.log('=== Testing Single-Device Login Kick ===');
    const srv = await ensureServerRunning();
    try {
        const client1 = createSocket();
        const client2 = createSocket();

        let client1Closed = false;
        client1.socket.on('close', () => {
            client1Closed = true;
        });

        // 1. Client 1 logs in
        await client1.connect('user_kick_test');
        console.log('[+] Client 1 logged in');

        // 2. Client 2 logs in with same account
        await client2.connect('user_kick_test');
        console.log('[+] Client 2 logged in with same account');

        // 3. Client 1 should receive kick frame
        const kickMsg = await client1.waitFor(msg => msg.type === 'kick');
        console.log('[+] Client 1 received kick frame:', kickMsg);
        assert.strictEqual(kickMsg.type, 'kick');
        assert.strictEqual(kickMsg.reason, 'logged_in_elsewhere');

        // 4. Wait a short moment to ensure client1 socket was destroyed by server
        await new Promise(r => setTimeout(r, 200));
        assert.strictEqual(client1Closed, true, 'Client 1 socket should be closed by server');
        console.log('[+] Client 1 socket is properly closed');

        client2.socket.destroy();
        console.log('=== Kick Test Passed Successfully! ===');
    } finally {
        if (srv) {
            console.log('[*] Stopping test server...');
            srv.kill();
        }
    }
}

testKick().then(() => process.exit(0)).catch(err => {
    console.error('[!] Test failed:', err);
    process.exit(1);
});
