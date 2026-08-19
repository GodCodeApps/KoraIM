package com.kora.imui.utils

import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecordHelper(private val outputDir: File) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTime: Long = 0

    fun startRecording(): Boolean {
        try {
            currentOutputFile = File(outputDir, "voice_${System.currentTimeMillis()}.aac")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentOutputFile!!.absolutePath)
                prepare()
                start()
            }
            startTime = System.currentTimeMillis()
            return true
        } catch (e: IOException) {
            Log.e("AudioRecordHelper", "startRecording failed", e)
            cancelRecording()
            return false
        } catch (e: IllegalStateException) {
            Log.e("AudioRecordHelper", "startRecording state error", e)
            cancelRecording()
            return false
        }
    }

    /**
     * 获取当前录音的最大振幅（0..32767），用于驱动音量波形动画。
     */
    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * @return Pair containing the file path and the duration in milliseconds
     */
    fun stopRecording(): Pair<String, Long>? {
        if (mediaRecorder == null || currentOutputFile == null) return null
        
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecordHelper", "stopRecording failed", e)
            cancelRecording()
            return null
        } finally {
            releaseRecorder()
        }
        
        val duration = System.currentTimeMillis() - startTime
        return Pair(currentOutputFile!!.absolutePath, duration)
    }

    fun cancelRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // Ignore
            } finally {
                releaseRecorder()
            }
        }
        
        currentOutputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentOutputFile = null
    }

    private fun releaseRecorder() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
