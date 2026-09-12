// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const fs = require('fs');
const path = require('path');

const configPath = path.resolve(__dirname, 'config.json');
let fileConfig = {};
if (fs.existsSync(configPath)) {
    try {
        fileConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (e) {
        console.warn(`[!] Failed to parse config.json, using defaults: ${e.message}`);
    }
}

function parseBoolean(value, fallback) {
    if (value === undefined || value === null || value === '') return fallback;
    if (typeof value === 'boolean') return value;
    return ['1', 'true', 'yes', 'on'].includes(String(value).trim().toLowerCase());
}

function resolveFile(value, fallback) {
    return path.resolve(__dirname, value || fallback);
}

const config = {
    port: Number(process.env.PORT || fileConfig.port || 8090),
    // TLS is enabled by default. Set TLS_ENABLED=false only for local/plaintext testing.
    tlsEnabled: parseBoolean(process.env.TLS_ENABLED, parseBoolean(fileConfig.tlsEnabled, true)),
    // Test-only wire logging. Disable this outside local debugging because it prints message content.
    wireLogEnabled: parseBoolean(process.env.WIRE_LOG_ENABLED, parseBoolean(fileConfig.wireLogEnabled, true)),
    tls: {
        certFile: resolveFile(
            process.env.TLS_CERT_FILE || (fileConfig.tls && fileConfig.tls.certFile),
            './cert/server.crt'
        ),
        keyFile: resolveFile(
            process.env.TLS_KEY_FILE || (fileConfig.tls && fileConfig.tls.keyFile),
            './cert/server.key'
        ),
        minVersion: process.env.TLS_MIN_VERSION || (fileConfig.tls && fileConfig.tls.minVersion) || 'TLSv1.2'
    },
    dbType: (process.env.DB_TYPE || fileConfig.dbType || 'sqlite').toLowerCase(),
    syncPageSize: Number(process.env.SYNC_PAGE_SIZE || fileConfig.syncPageSize || 100),
    sqlite: {
        filename: process.env.SQLITE_FILE || (fileConfig.sqlite && fileConfig.sqlite.filename) || './kora_im.db'
    },
    mysql: {
        host: process.env.MYSQL_HOST || (fileConfig.mysql && fileConfig.mysql.host) || '127.0.0.1',
        port: Number(process.env.MYSQL_PORT || (fileConfig.mysql && fileConfig.mysql.port) || 3306),
        user: process.env.MYSQL_USER || (fileConfig.mysql && fileConfig.mysql.user) || 'root',
        password: process.env.MYSQL_PASSWORD || (fileConfig.mysql && fileConfig.mysql.password) || '',
        database: process.env.MYSQL_DATABASE || (fileConfig.mysql && fileConfig.mysql.database) || 'kora_im',
        connectionLimit: Number(process.env.MYSQL_CONNECTION_LIMIT || (fileConfig.mysql && fileConfig.mysql.connectionLimit) || 10)
    }
};

module.exports = config;
