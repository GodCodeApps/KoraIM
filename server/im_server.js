// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const net = require('net');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8090);

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

    socket.on('end', () => console.log('[-] Client disconnected'));
    socket.on('error', (error) => console.error(`[x] Socket error: ${error.message}`));
});

function handleFrame(socket, line) {
    try {
        const envelope = JSON.parse(line);
        if (envelope.type !== 'message' || !envelope.payload || !envelope.messageId) {
            throw new Error('Unsupported frame');
        }

        writeFrame(socket, { type: 'ack', messageId: envelope.messageId });

        const received = envelope.payload;
        const reply = {
            ...received,
            id: 0,
            messageId: crypto.randomUUID(),
            account: 'server_bot',
            direct: 1,
            status: 1,
            time: Date.now()
        };
        writeFrame(socket, { type: 'message', messageId: reply.messageId, payload: reply });
    } catch (error) {
        console.error(`[!] Invalid frame: ${error.message}`);
    }
}

server.on('error', (error) => console.error(`[x] Server error: ${error.message}`));
server.listen(PORT, () => console.log(`KoraIM test server listening on ${PORT}`));
