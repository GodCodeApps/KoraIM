package com.kora.imui.utils

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:14:20
 * @Description:
 */
object PhotoMetadataUtils {
    fun getBitmapBound(resolver: ContentResolver, uri: Uri?): Point? {
        var `is`: InputStream? = null
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            `is` = resolver.openInputStream(uri!!)
            BitmapFactory.decodeStream(`is`, null, options)
            val width = options.outWidth
            val height = options.outHeight
            Point(width, height)
        } catch (e: FileNotFoundException) {
            Point(0, 0)
        } finally {
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
