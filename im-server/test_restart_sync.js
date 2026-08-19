// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const assert = require('assert');

const PORT = 8090;

async function runRestartTest() {
    console.log('=== Verifying Cold Restart Persistence ===');
    const client = new net.Socket();
    client.setEncoding('utf8');

    await new Promise((resolve, reject) => {
        client.connect(PORT, '127.0.0.1', () => {
            client.write(JSON.stringify({ type: 'login', account: 'user_bob' }) + '\n');
            setTimeout(resolve, 100);
        });
        client.on('error', reject);
    });

    const responsePromise = new Promise((resolve) => {
        client.on('data', data => {
            const line = data.toString().trim();
            if (line) resolve(JSON.parse(line));
        });
    });

    // 重新从 0 开始拉取
    client.write(JSON.stringify({ type: 'sync', cursor: 0 }) + '\n');
    const res = await responsePromise;

    console.log('Sync result after server restart:', JSON.stringify(res, null, 2));
    assert.strictEqual(res.type, 'sync_result');
    assert.ok(res.events.length >= 2, 'Should have preserved at least 2 historical events across restart');
    console.log('=== Cold Restart Persistence Verified Successfully! ===');
    client.destroy();
}

runRestartTest().catch(err => {
    console.error(err);
    process.exit(1);
});
