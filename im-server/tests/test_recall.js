const assert = require('assert');
const fs = require('fs');
const path = require('path');
const SqliteStorage = require('../storage/sqlite_storage');

(async () => {
    const filename = 'tests/.tmp_recall_test.db';
    const files = [filename, `${filename}-wal`, `${filename}-shm`].map(f => path.resolve(process.cwd(), f));
    files.forEach(f => { if (fs.existsSync(f)) fs.unlinkSync(f); });
    const storage = new SqliteStorage({ sqlite: { filename }, syncPageSize: 100 });
    try {
        await storage.init();
        const messageId = `recall_${Date.now()}`;
        await storage.saveMessageAndEvents('alice', 'bob', {
            messageId, sessionType: 1, type: 1, attachment: '{"content":"hello"}', extra: '', status: 2, time: Date.now()
        });
        const forbidden = await storage.recallMessage('bob', messageId, 120000);
        assert.strictEqual(forbidden.errorCode, 'FORBIDDEN');
        const recalled = await storage.recallMessage('alice', messageId, 120000);
        assert.strictEqual(recalled.success, true);
        assert.strictEqual(recalled.message.recalled, true);
        const bobSync = await storage.getSyncEvents('bob', 0, 100);
        assert.ok(bobSync.events.some(e => e.eventType === 'recall' && e.payload.messageId === messageId));
        const duplicate = await storage.recallMessage('alice', messageId, 120000);
        assert.strictEqual(duplicate.success, true);
        const oldMessageId = `old_${Date.now()}`;
        await storage.saveMessageAndEvents('alice', 'bob', {
            messageId: oldMessageId, sessionType: 1, type: 1, attachment: '{}', extra: '', status: 1,
            time: Date.now() - 121000
        });
        const expired = await storage.recallMessage('alice', oldMessageId, 120000);
        assert.strictEqual(expired.errorCode, 'EXPIRED');
        console.log('Recall storage tests passed');
    } finally {
        await storage.close();
        files.forEach(f => { if (fs.existsSync(f)) fs.unlinkSync(f); });
    }
})().catch(error => { console.error(error); process.exit(1); });
