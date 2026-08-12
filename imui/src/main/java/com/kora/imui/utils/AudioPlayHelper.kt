package com.kora.imui.utils

import android.media.MediaPlayer
import android.util.Log

object AudioPlayHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPath: String? = null
    private var onCompletionListener: (() -> Unit)? = null

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
