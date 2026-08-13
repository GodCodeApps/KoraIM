package com.kora.imui.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object VideoMetadataUtils {
    fun createCover(context: Context, source: String): String {
        if (source.isBlank()) return ""
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(source)
            if (uri.scheme.isNullOrBlank()) retriever.setDataSource(source)
            else retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return ""
            val cover = File(context.cacheDir, "video_cover_${System.nanoTime()}.jpg")
            FileOutputStream(cover).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            cover.absolutePath
        } catch (_: Exception) {
            ""
        } finally {
            runCatching { retriever.release() }
        }
    }
}
