// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
const SqliteStorage = require('./sqlite_storage');
const MysqlStorage = require('./mysql_storage');

function createStorage(config) {
    const dbType = (config.dbType || 'sqlite').toLowerCase();
    switch (dbType) {
        case 'sqlite':
            return new SqliteStorage(config);
        case 'mysql':
            return new MysqlStorage(config);
        default:
            throw new Error(`Unsupported dbType: ${dbType}. Supported types: 'sqlite', 'mysql'`);
    }
}

module.exports = {
    createStorage
};
