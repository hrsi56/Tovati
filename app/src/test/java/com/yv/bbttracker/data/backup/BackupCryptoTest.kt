package com.yv.bbttracker.data.backup

import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val createdAt = Instant.parse("2026-07-16T20:00:00Z")

    @Test
    fun `round trip preserves arbitrary UTF-8 payload`() {
        val payload =
            """{"schemaVersion":1,"notes":"מדידה, עם \"מרכאות\"\nושורה חדשה 🌡️"}"""
        val password = "סיסמת גיבוי חזקה 123!".toCharArray()

        val encrypted = BackupCrypto.encrypt(payload, password, createdAt)

        assertFalse(encrypted.contains("מדידה"))
        assertEquals(payload, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun `same payload password and timestamp produce independent randomized envelopes`() {
        val payload = "private fertility data"
        val password = "same strong password"

        val first = BackupCrypto.encrypt(payload, password, createdAt)
        val second = BackupCrypto.encrypt(payload, password, createdAt)
        val firstRoot = Json.parseToJsonElement(first).jsonObject
        val secondRoot = Json.parseToJsonElement(second).jsonObject

        assertNotEquals(first, second)
        assertNotEquals(
            firstRoot.getValue("kdf").jsonObject.value("saltBase64"),
            secondRoot.getValue("kdf").jsonObject.value("saltBase64"),
        )
        assertNotEquals(
            firstRoot.getValue("cipher").jsonObject.value("ivBase64"),
            secondRoot.getValue("cipher").jsonObject.value("ivBase64"),
        )
        assertNotEquals(firstRoot.value("ciphertextBase64"), secondRoot.value("ciphertextBase64"))
        assertEquals(payload, BackupCrypto.decrypt(first, password))
        assertEquals(payload, BackupCrypto.decrypt(second, password))
    }

    @Test
    fun `malformed or incomplete envelopes use the generic decryption error`() {
        listOf(
            "",
            "not-json",
            "{}",
            "{\"format\":\"com.yv.bbttracker.backup\"}",
        ).forEach { malformed ->
            assertGenericFailure { BackupCrypto.decrypt(malformed, "irrelevant password") }
        }
    }

    @Test
    fun `envelope contains exact supported algorithms and parameter sizes`() {
        val encrypted = BackupCrypto.encrypt("{}", "password", createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject

        assertEquals(
            setOf("format", "version", "createdAt", "kdf", "cipher", "ciphertextBase64"),
            root.keys,
        )
        assertEquals("com.yv.bbttracker.backup", root.value("format"))
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        assertEquals(createdAt.toString(), root.value("createdAt"))

        val kdf = root.getValue("kdf").jsonObject
        assertEquals(setOf("algorithm", "iterations", "saltBase64"), kdf.keys)
        assertEquals("PBKDF2WithHmacSHA256", kdf.value("algorithm"))
        assertEquals(310_000, kdf.getValue("iterations").jsonPrimitive.int)
        assertTrue(Base64.getDecoder().decode(kdf.value("saltBase64")).size >= 16)

        val cipher = root.getValue("cipher").jsonObject
        assertEquals(setOf("algorithm", "ivBase64"), cipher.keys)
        assertEquals("AES/GCM/NoPadding", cipher.value("algorithm"))
        assertEquals(12, Base64.getDecoder().decode(cipher.value("ivBase64")).size)
    }

    @Test
    fun `envelope decrypts with independent 256-bit implementation`() {
        val payload = "{\"notes\":\"עברית and UTF-8 🌡️\"}"
        val password = "independent implementation password".toCharArray()
        val encrypted = BackupCrypto.encrypt(payload, password, createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject
        val kdf = root.getValue("kdf").jsonObject
        val cipherParameters = root.getValue("cipher").jsonObject
        val salt = Base64.getDecoder().decode(kdf.value("saltBase64"))
        val iv = Base64.getDecoder().decode(cipherParameters.value("ivBase64"))
        val ciphertext = Base64.getDecoder().decode(root.value("ciphertextBase64"))
        val keySpec = PBEKeySpec(password, salt, 310_000, 256)
        var keyBytes: ByteArray? = null

        try {
            keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
            val authenticatedHeader = buildJsonObject {
                put("format", JsonPrimitive(root.value("format")))
                put("version", JsonPrimitive(root.getValue("version").jsonPrimitive.int))
                put("createdAt", JsonPrimitive(root.value("createdAt")))
                put("kdf", kdf)
                put("cipher", cipherParameters)
            }.toString().toByteArray(Charsets.UTF_8)
            val independentlyDecrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                updateAAD(authenticatedHeader)
                doFinal(ciphertext)
            }

            assertEquals(payload, independentlyDecrypted.toString(Charsets.UTF_8))
            independentlyDecrypted.fill(0)
            authenticatedHeader.fill(0)
        } finally {
            keySpec.clearPassword()
            keyBytes?.fill(0)
            password.fill('\u0000')
        }
    }

    @Test
    fun `wrong password is rejected with generic error`() {
        val encrypted = BackupCrypto.encrypt("sensitive", "correct password", createdAt)

        assertGenericFailure {
            BackupCrypto.decrypt(encrypted, "incorrect password")
        }
    }

    @Test
    fun `modified ciphertext is rejected with generic error`() {
        val encrypted = BackupCrypto.encrypt("sensitive", "correct password", createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject
        val ciphertext = Base64.getDecoder().decode(root.value("ciphertextBase64"))
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 0x01).toByte()
        val tampered = root.withField(
            "ciphertextBase64",
            JsonPrimitive(Base64.getEncoder().encodeToString(ciphertext)),
        ).toString()

        assertGenericFailure {
            BackupCrypto.decrypt(tampered, "correct password")
        }
    }

    @Test
    fun `modified valid createdAt timestamp is rejected as tampering`() {
        val encrypted = BackupCrypto.encrypt("sensitive", "correct password", createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject
        val tampered = root.withField(
            "createdAt",
            JsonPrimitive("2026-07-17T20:00:00Z"),
        ).toString()

        assertGenericFailure {
            BackupCrypto.decrypt(tampered, "correct password")
        }
    }

    @Test
    fun `unsupported envelope version has a distinct actionable error`() {
        val encrypted = BackupCrypto.encrypt("sensitive", "correct password", createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject
        val unsupported = root.withField("version", JsonPrimitive(2)).toString()

        try {
            BackupCrypto.decrypt(unsupported, "correct password")
            fail("Expected UnsupportedBackupVersionException")
        } catch (_: UnsupportedBackupVersionException) {
            // Expected: the UI can tell the user that a newer app/schema is required.
        }
    }

    @Test
    fun `unknown envelope field is rejected`() {
        val encrypted = BackupCrypto.encrypt("sensitive", "correct password", createdAt)
        val root = Json.parseToJsonElement(encrypted).jsonObject
        val unsupported = buildJsonObject {
            root.forEach { (key, value) -> put(key, value) }
            put("unsupported", JsonPrimitive(true))
        }.toString()

        assertGenericFailure {
            BackupCrypto.decrypt(unsupported, "correct password")
        }
    }

    @Test
    fun `inspect returns validated metadata`() {
        val encrypted = BackupCrypto.encrypt("{}", "password", createdAt)

        assertEquals(
            BackupMetadata(createdAt = createdAt, version = BackupCrypto.VERSION),
            BackupCrypto.inspect(encrypted),
        )
    }

    @Test
    fun `large payload round trips`() {
        val payload = buildString {
            repeat(1_100) { index -> append("$index:טמפרטורה=36.55\n") }
        }
        val encrypted = BackupCrypto.encrypt(payload, "large backup password", createdAt)

        assertEquals(payload, BackupCrypto.decrypt(encrypted, "large backup password"))
    }

    private fun assertGenericFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected BackupCryptoException")
        } catch (exception: BackupCryptoException) {
            assertEquals(BackupCrypto.DECRYPTION_ERROR_MESSAGE, exception.message)
            assertEquals(null, exception.cause)
        }
    }

    private fun JsonObject.value(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.withField(key: String, value: JsonPrimitive): JsonObject = buildJsonObject {
        this@withField.forEach { (existingKey, existingValue) ->
            put(existingKey, if (existingKey == key) value else existingValue)
        }
    }
}
