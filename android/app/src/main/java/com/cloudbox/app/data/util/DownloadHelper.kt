package com.cloudbox.app.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object DownloadHelper {
    fun publishToDownloads(context: Context, source: File, mimeType: String?): Uri? {
        val folder = "Cloudbox"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folder")
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                uri
            }.getOrNull()
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    folder
                )
                dir.mkdirs()
                val target = File(dir, source.name)
                source.copyTo(target, overwrite = true)
                null
            }.getOrNull()
        }
    }
}