package com.example.nearworkthesis.core.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi

fun writeBytesToDownloads(context: Context, filename: String, mimeType: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeBytesToDownloadsScoped(context, filename, mimeType, bytes)
    } else {
        error("Downloads export requires Android 10 or newer.")
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun writeBytesToDownloadsScoped(
    context: Context,
    filename: String,
    mimeType: String,
    bytes: ByteArray
) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, filename)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val uri = resolver.insert(collection, values) ?: error("Unable to create download.")
    resolver.openOutputStream(uri).use { output ->
        if (output == null) error("Unable to open download stream.")
        output.write(bytes)
        output.flush()
    }

    val done = ContentValues().apply {
        put(MediaStore.Downloads.IS_PENDING, 0)
    }
    resolver.update(uri, done, null, null)
}
