package com.yv.bbttracker.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentGateway(context: Context) {
    private val applicationContext = context.applicationContext
    private val resolver: ContentResolver = applicationContext.contentResolver
    private val shareableBackupFile: File
        get() = File(applicationContext.filesDir, "backups/$LATEST_BACKUP_FILE_NAME")

    suspend fun write(uriValue: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriValue)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("Unable to open output document")
    }

    suspend fun readText(uriValue: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriValue)
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Unable to open input document")
    }

    suspend fun storeLatestEncryptedBackup(envelope: String) = withContext(Dispatchers.IO) {
        BackupCrypto.inspect(envelope)
        val file = shareableBackupFile
        check(file.parentFile?.mkdirs() != false) { "Unable to create backup directory" }
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(envelope.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    suspend fun hasShareableBackup(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = shareableBackupFile
            if (!file.isFile || file.length() == 0L) return@runCatching false
            BackupCrypto.inspect(file.readText(Charsets.UTF_8))
            true
        }.getOrDefault(false)
    }

    suspend fun shareableBackup(): ShareableBackupDocument = withContext(Dispatchers.IO) {
        val file = shareableBackupFile
        check(file.isFile && file.length() > 0L) { "No shareable backup" }
        val metadata = BackupCrypto.inspect(file.readText(Charsets.UTF_8))
        ShareableBackupDocument(
            uri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                file,
            ).toString(),
            createdAt = metadata.createdAt,
        )
    }

    suspend fun deleteShareableBackup() = withContext(Dispatchers.IO) {
        val file = shareableBackupFile
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Unable to delete shareable backup")
        }
    }

    private companion object {
        const val LATEST_BACKUP_FILE_NAME = "tovati-latest-backup.bbt.json"
    }
}

data class ShareableBackupDocument(
    val uri: String,
    val createdAt: java.time.Instant,
)
