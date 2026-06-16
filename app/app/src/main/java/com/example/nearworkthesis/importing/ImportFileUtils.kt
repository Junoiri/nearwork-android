package com.example.nearworkthesis.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

internal fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx == -1) return@use null
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(idx)
        }
    }.getOrNull()
}

internal fun readAllBytesWithLimit(
    context: Context,
    uri: Uri,
    maxBytes: Int
): ByteArray {
    require(maxBytes > 0)
    val inputStream = context.contentResolver.openInputStream(uri) ?: error("Unable to open input stream.")
    inputStream.use { stream ->
        val buffer = ByteArray(8 * 1024)
        var total = 0
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) error("File too large.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

