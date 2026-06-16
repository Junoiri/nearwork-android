package com.example.nearworkthesis.importing.howfar

import android.content.Context
import java.io.File
import java.util.Properties

interface HowfarUf2Archive {
    suspend fun saveLatest(profileId: Long, filename: String, bytes: ByteArray)
    suspend fun loadLatest(profileId: Long): HowfarUf2Snapshot?
}

data class HowfarUf2Snapshot(
    val filename: String,
    val bytes: ByteArray,
    val savedAtEpochMillis: Long
)

class AndroidHowfarUf2Archive(
    private val appContext: Context
) : HowfarUf2Archive {

    override suspend fun saveLatest(profileId: Long, filename: String, bytes: ByteArray) {
        val dir = ensureDir()
        val dataFile = File(dir, dataFilename(profileId))
        val metaFile = File(dir, metaFilename(profileId))

        dataFile.writeBytes(bytes)
        val meta = Properties().apply {
            setProperty(KEY_FILENAME, filename)
            setProperty(KEY_SAVED_AT, System.currentTimeMillis().toString())
        }
        metaFile.outputStream().use { meta.store(it, null) }
    }

    override suspend fun loadLatest(profileId: Long): HowfarUf2Snapshot? {
        val dir = ensureDir()
        val dataFile = File(dir, dataFilename(profileId))
        val metaFile = File(dir, metaFilename(profileId))
        if (!dataFile.exists()) return null

        val meta = Properties()
        if (metaFile.exists()) {
            metaFile.inputStream().use { meta.load(it) }
        }

        val filename = meta.getProperty(KEY_FILENAME) ?: "howfar_latest.uf2"
        val savedAt = meta.getProperty(KEY_SAVED_AT)?.toLongOrNull() ?: dataFile.lastModified()
        return HowfarUf2Snapshot(
            filename = filename,
            bytes = dataFile.readBytes(),
            savedAtEpochMillis = savedAt
        )
    }

    private fun ensureDir(): File {
        val dir = File(appContext.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun dataFilename(profileId: Long): String = "howfar_${profileId}_latest.uf2"
    private fun metaFilename(profileId: Long): String = "howfar_${profileId}_latest.properties"

    private companion object {
        const val DIRECTORY_NAME = "howfar"
        const val KEY_FILENAME = "filename"
        const val KEY_SAVED_AT = "saved_at"
    }
}
