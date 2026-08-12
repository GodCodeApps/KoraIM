const net = require('net');
const crypto = require('crypto');

const PORT = 8090;

// Create a TCP server
const server = net.createServer((socket) => {
    console.log(`[+] New client connected: ${socket.remoteAddress}:${socket.remotePort}`);

    // Set encoding to UTF-8 so data arrives as string
    socket.setEncoding('utf8');

    socket.on('data', (data) => {
        console.log(`[↓] Received data: ${data}`);

        try {
            // Android client sends a JSON string
            const receivedMsg = JSON.parse(data);

            // Extract text from attachment
            let text = "";
            try {
                if (receivedMsg.attachment) {
                    const attachObj = JSON.parse(receivedMsg.attachment);
                    text = attachObj.content || "";
                }
            } catch(e) {}

            // Construct an auto-reply message based on the incoming message
            // msg.direct = 1 (MsgDirection.IN means received message)
            // msg.status = 1 (SUCCESS)
            const replyMsg = {
                ...receivedMsg,
                messageId: crypto.randomUUID(),      // Generate a new msg id
                account: "server_bot",               // 改变发送者账号，不要跟自己一样
                direct: 1,                           // 1 = IN (Received msg on Android)
                status: 1,                           // 1 = SUCCESS
                time: Date.now(),
                attachment: JSON.stringify({ content: `[服务端收到]: ${text}` }),
                extra: ""
            };

            // Send back the JSON string
            const replyString = JSON.stringify(replyMsg);
            socket.write(replyString);
            console.log(`[↑] Sent auto-reply: ${replyString}`);
            
        } catch (e) {
            console.error(`[!] Failed to parse incoming JSON data: ${e.message}`);
        }
    });

    socket.on('end', () => {
        console.log(`[-] Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
    });

    socket.on('error', (err) => {
        console.error(`[x] Socket error: ${err.message}`);
    });
});

server.on('error', (err) => {
    console.error(`[x] Server error: ${err.message}`);
});

server.listen(PORT, () => {
    console.log(`=========================================`);
    console.log(`🚀 IM Test Server is running on port ${PORT}`);
    console.log(`Waiting for connections...`);
    console.log(`=========================================`);
});
