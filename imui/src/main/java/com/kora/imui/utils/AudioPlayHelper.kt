package com.kora.imui.utils

import android.media.MediaPlayer
import android.util.Log

/**
 * 语音播放器单例：
 * 保证全局单路音频互斥播放，避免多条语音同时发声。
 */
object AudioPlayHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPath: String? = null
    private var onCompletionListener: (() -> Unit)? = null

    /** 播放指定本地音频文件，并注册播放结束回调 */
    fun playAudio(path: String, onCompletion: () -> Unit) {
        if (currentPlayingPath == path && mediaPlayer?.isPlaying == true) {
            // Already playing this audio, stop it
            stopAudio()
            return
        }

        stopAudio()
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
            currentPlayingPath = path
            onCompletionListener = onCompletion
            
            mediaPlayer?.setOnCompletionListener {
                stopAudio()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayHelper", "playAudio failed", e)
            stopAudio()
        }
    }

    fun stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
            } catch (e: Exception) {
                // Ignore
            } finally {
                mediaPlayer = null
                currentPlayingPath = null
                onCompletionListener?.invoke()
                onCompletionListener = null
            }
        }
    }
}
