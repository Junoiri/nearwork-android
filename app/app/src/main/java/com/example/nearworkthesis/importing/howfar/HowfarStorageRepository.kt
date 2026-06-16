/** Storage access layer for HowFar UF2 files; mirrors the file-based flow of howfar/cli/read.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface HowfarStorageRepository {
    val state: StateFlow<HowfarStorageState>

    fun refresh()
    fun setDeviceTreeUri(uri: Uri?)
    fun deviceTreeUri(): Uri?

    suspend fun readDataUf2(examIdentifier: String? = null): ByteArray
    suspend fun readConfigUf2(): ByteArray?
    suspend fun writeConfigUf2(bytes: ByteArray)

    fun close()
}

sealed interface HowfarStorageState {
    data object Disconnected : HowfarStorageState
    data class Ready(val info: HowfarDeviceInfo) : HowfarStorageState
    data class Error(val message: String) : HowfarStorageState
}

data class HowfarDeviceInfo(
    val treeUri: Uri,
    val displayName: String?
)

private val Context.howfarStorageStore by preferencesDataStore("howfar_storage")

// Android SAF-backed storage access: mirrors the Python CLI's file-based approach (explicit UF2 files).
class AndroidHowfarStorageRepository(
    private val appContext: Context
) : HowfarStorageRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbManager = checkNotNull(appContext.getSystemService(Context.USB_SERVICE) as? UsbManager) {
        "UsbManager not available"
    }
    private val keyDeviceTreeUri = stringPreferencesKey("howfar_device_tree_uri")

    private val _state = MutableStateFlow<HowfarStorageState>(HowfarStorageState.Disconnected)
    override val state: StateFlow<HowfarStorageState> = _state.asStateFlow()
    @Volatile
    private var latestDeviceTreeUri: String? = null
    private var usbReceiverRegistered = false
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()
            }
        }
    }

    init {
        registerUsbReceiver()
        scope.launch {
            appContext.howfarStorageStore.data
                .map { prefs -> prefs[keyDeviceTreeUri] }
                .collect { uriString ->
                    latestDeviceTreeUri = uriString
                    updateState(uriString)
                }
        }
    }

    override fun refresh() {
        scope.launch {
            val uriString = latestDeviceTreeUri ?: appContext.howfarStorageStore.data
                .map { it[keyDeviceTreeUri] }
                .first().also { latestDeviceTreeUri = it }
            updateState(uriString)
        }
    }

    override fun setDeviceTreeUri(uri: Uri?) {
        val value = uri?.toString()
        latestDeviceTreeUri = value
        scope.launch {
            appContext.howfarStorageStore.edit { prefs ->
                if (value == null) {
                    prefs.remove(keyDeviceTreeUri)
                } else {
                    prefs[keyDeviceTreeUri] = value
                }
            }
        }
        updateState(value)
    }

    override fun deviceTreeUri(): Uri? {
        val current = _state.value
        return if (current is HowfarStorageState.Ready) current.info.treeUri else null
    }

    // Mirrors Python behavior: read {examIdentifier}.UF2 if present, else optodata.uf2.
    override suspend fun readDataUf2(examIdentifier: String?): ByteArray {
        val root = requireRoot()
        val file = resolveDataFile(root, examIdentifier)
            ?: error("HowFar data UF2 file not found.")
        return readDocumentBytes(file)
    }

    override suspend fun readConfigUf2(): ByteArray? {
        val root = requireRootOrNull() ?: return null
        val file = resolveConfigFile(root) ?: return null
        return readDocumentBytes(file)
    }

    override suspend fun writeConfigUf2(bytes: ByteArray) {
        val root = requireRoot()
        val file = resolveConfigFile(root) ?: root.createFile("application/octet-stream", CONFIG_FILE_NAME)
        requireNotNull(file) { "Unable to create config file on device." }
        writeDocumentBytes(file, bytes)
    }

    override fun close() {
        if (usbReceiverRegistered) {
            appContext.unregisterReceiver(usbReceiver)
            usbReceiverRegistered = false
        }
        scope.cancel()
    }

    private fun requireRoot(): DocumentFile {
        val root = requireRootOrNull()
        return root ?: error("HowFar device storage not selected.")
    }

    private fun requireRootOrNull(): DocumentFile? {
        val current = _state.value
        if (current !is HowfarStorageState.Ready) return null
        return DocumentFile.fromTreeUri(appContext, current.info.treeUri)
    }

    private fun updateState(uriString: String?) {
        if (usbManager.deviceList.isEmpty()) {
            _state.value = HowfarStorageState.Disconnected
            return
        }
        if (uriString.isNullOrBlank()) {
            _state.value = HowfarStorageState.Disconnected
            return
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (uri == null) {
            _state.value = HowfarStorageState.Error("Invalid HowFar storage URI.")
            return
        }
        val doc = DocumentFile.fromTreeUri(appContext, uri)
        if (doc == null || !doc.canRead() || !isAccessible(doc)) {
            _state.value = HowfarStorageState.Error("HowFar storage not accessible.")
            return
        }
        _state.value = HowfarStorageState.Ready(
            info = HowfarDeviceInfo(treeUri = uri, displayName = doc.name)
        )
    }

    private fun registerUsbReceiver() {
        if (usbReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onSuccess {
            usbReceiverRegistered = true
        }
    }

    private fun isAccessible(doc: DocumentFile): Boolean {
        return runCatching {
            if (!doc.isDirectory) return@runCatching false
            doc.listFiles()
            true
        }.getOrDefault(false)
    }

    private fun resolveConfigFile(root: DocumentFile): DocumentFile? {
        return findFileIgnoreCase(root, CONFIG_FILE_NAME)
    }

    // Match README behavior: prefer exam ID file name, fall back to optodata.uf2.
    private fun resolveDataFile(root: DocumentFile, examIdentifier: String?): DocumentFile? {
        val candidates = ArrayList<String>()
        if (!examIdentifier.isNullOrBlank()) {
            candidates.add("${examIdentifier}.UF2")
        }
        candidates.add(DATA_FILE_NAME)
        return findFileIgnoreCase(root, candidates)
    }

    private fun findFileIgnoreCase(root: DocumentFile, name: String): DocumentFile? {
        return findFileIgnoreCase(root, listOf(name))
    }

    private fun findFileIgnoreCase(root: DocumentFile, names: List<String>): DocumentFile? {
        val candidates = names.map { it.lowercase() }.toSet()
        return root.listFiles().firstOrNull { file ->
            val filename = file.name?.lowercase() ?: return@firstOrNull false
            candidates.contains(filename)
        }
    }

    private suspend fun readDocumentBytes(document: DocumentFile): ByteArray {
        val uri = document.uri
        return withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read HowFar UF2 file.")
            }.getOrElse { throwable ->
                _state.value = HowfarStorageState.Error("HowFar storage not accessible.")
                throw throwable
            }
        }
    }

    private suspend fun writeDocumentBytes(document: DocumentFile, bytes: ByteArray) {
        val uri = document.uri
        withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: error("Unable to write HowFar UF2 file.")
            }.getOrElse { throwable ->
                _state.value = HowfarStorageState.Error("HowFar storage not accessible.")
                throw throwable
            }
        }
    }

    private companion object {
        const val CONFIG_FILE_NAME = "optoconf.uf2"
        const val DATA_FILE_NAME = "optodata.uf2"
    }
}








