// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const PORT = Number(process.env.PORT || 8090);
const clients = new Map();

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
        if (envelope.type !== 'message' || !envelope.payload || !envelope.messageId) {
            throw new Error('Unsupported frame');
        }
        if (!socket.account) throw new Error('Login required');

        const recipient = clients.get(envelope.payload.sessionId);
        if (!recipient || recipient.destroyed) {
            console.log(`[>] ${socket.account} -> ${envelope.payload.sessionId} (offline)`);
            return;
        }

        const incoming = {
            ...envelope.payload,
            id: 0,
            sessionId: socket.account,
            account: socket.account,
            direct: 1,
            status: 1,
            time: Date.now()
        };
        writeFrame(recipient, { type: 'message', messageId: incoming.messageId, payload: incoming });
        writeFrame(socket, { type: 'ack', messageId: envelope.messageId });
        console.log(`[>] ${socket.account} -> ${recipient.account}`);
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
