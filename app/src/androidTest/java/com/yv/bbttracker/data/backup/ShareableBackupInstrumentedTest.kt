package com.yv.bbttracker.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareableBackupInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = DocumentGateway(context)

    @After
    fun cleanUp() = runBlocking {
        gateway.deleteShareableBackup()
    }

    @Test
    fun encryptedBackupIsKeptPrivatelyAndExposedOnlyThroughReadableContentUri() = runBlocking {
        val createdAt = Instant.parse("2026-07-18T08:15:00Z")
        val envelope = BackupCrypto.encrypt(
            payload = """{"schemaVersion":4}""",
            password = "strong password",
            createdAt = createdAt,
        )

        gateway.storeLatestEncryptedBackup(envelope)

        assertTrue(gateway.hasShareableBackup())
        val shareable = gateway.shareableBackup()
        assertEquals(createdAt, shareable.createdAt)
        val uri = Uri.parse(shareable.uri)
        assertEquals("content", uri.scheme)
        val sharedText = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals(envelope, sharedText)

        gateway.deleteShareableBackup()
        assertFalse(gateway.hasShareableBackup())
    }
}
