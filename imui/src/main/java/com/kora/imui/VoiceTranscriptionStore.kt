package com.kora.imui

import android.content.Context

/** Stores voice-message transcripts locally; transcripts are not added to network payloads. */
internal object VoiceTranscriptionStore {
    private const val PREFS_NAME = "im_voice_transcriptions"
    private const val KEY_PREFIX = "message_"

    fun get(context: Context, messageId: String): String =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + messageId, "")
            .orEmpty()

    fun put(context: Context, messageId: String, text: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + messageId, text)
            .apply()
    }

    fun remove(context: Context, messageId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + messageId)
            .apply()
    }
}
