package com.yv.bbttracker.data.backup

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Password-based encryption for portable Tovati backups.
 *
 * Passwords are accepted as [CharArray]s so callers can erase their own buffer once the operation
 * completes. This object never retains the password. The String overloads exist for convenience
 * and erase the temporary character array they create.
 */
object BackupCrypto {
    const val FORMAT: String = "com.yv.bbttracker.backup"
    const val VERSION: Int = 1
    const val KDF_ALGORITHM: String = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS: Int = 310_000
    const val CIPHER_ALGORITHM: String = "AES/GCM/NoPadding"
    const val DECRYPTION_ERROR_MESSAGE: String = "Unable to decrypt backup"

    private const val KEY_SIZE_BITS = 256
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BITS = 128
    private const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / Byte.SIZE_BITS

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    /** Encrypts an arbitrary UTF-8 [payload] and returns the versioned JSON envelope. */
    fun encrypt(
        payload: String,
        password: CharArray,
        createdAt: Instant = Instant.now(),
        secureRandom: SecureRandom = SecureRandom(),
    ): String {
        val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE_BYTES).also(secureRandom::nextBytes)
        val plaintext = payload.toByteArray(Charsets.UTF_8)
        var authenticatedMetadata: ByteArray? = null

        return try {
            val header = AuthenticatedHeader(
                format = FORMAT,
                version = VERSION,
                createdAt = createdAt.toString(),
                kdf = KdfEnvelope(
                    algorithm = KDF_ALGORITHM,
                    iterations = KDF_ITERATIONS,
                    saltBase64 = Base64.getEncoder().encodeToString(salt),
                ),
                cipher = CipherEnvelope(
                    algorithm = CIPHER_ALGORITHM,
                    ivBase64 = Base64.getEncoder().encodeToString(iv),
                ),
            )
            val headerBytes = header.toAuthenticatedBytes().also { authenticatedMetadata = it }
            val ciphertext = withDerivedKey(password, salt) { key ->
                Cipher.getInstance(CIPHER_ALGORITHM).run {
                    init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
                    updateAAD(headerBytes)
                    doFinal(plaintext)
                }
            }

            json.encodeToString(
                BackupEnvelope(
                    format = header.format,
                    version = header.version,
                    createdAt = header.createdAt,
                    kdf = header.kdf,
                    cipher = header.cipher,
                    ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
                ),
            )
        } finally {
            // The returned String is necessarily immutable, but mutable plaintext/key material is
            // erased as soon as the provider has completed the operation.
            plaintext.fill(0)
            salt.fill(0)
            iv.fill(0)
            authenticatedMetadata?.fill(0)
        }
    }

    fun encrypt(
        payload: String,
        password: String,
        createdAt: Instant = Instant.now(),
        secureRandom: SecureRandom = SecureRandom(),
    ): String {
        val passwordBuffer = password.toCharArray()
        return try {
            encrypt(payload, passwordBuffer, createdAt, secureRandom)
        } finally {
            passwordBuffer.fill('\u0000')
        }
    }

    /**
     * Decrypts a supported envelope.
     *
     * Malformed/unsupported envelopes, a wrong password, and failed authentication deliberately
     * produce the same public error so callers do not leak cryptographic details.
     */
    fun decrypt(envelopeJson: String, password: CharArray): String {
        var plaintext: ByteArray? = null
        var salt: ByteArray? = null
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var authenticatedMetadata: ByteArray? = null

        try {
            val parsed = parseAndValidate(envelopeJson)
            val decodedSalt = decodeBase64(parsed.kdf.saltBase64).also { salt = it }
            val decodedIv = decodeBase64(parsed.cipher.ivBase64).also { iv = it }
            val decodedCiphertext = decodeBase64(parsed.ciphertextBase64).also { ciphertext = it }

            if (decodedSalt.size < SALT_SIZE_BYTES || decodedIv.size != IV_SIZE_BYTES) {
                throw InvalidBackupException()
            }
            if (decodedCiphertext.size < GCM_TAG_SIZE_BYTES) {
                throw InvalidBackupException()
            }
            val headerBytes = parsed.toAuthenticatedHeader().toAuthenticatedBytes().also {
                authenticatedMetadata = it
            }

            plaintext = withDerivedKey(password, decodedSalt) { key ->
                Cipher.getInstance(CIPHER_ALGORITHM).run {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, decodedIv))
                    updateAAD(headerBytes)
                    doFinal(decodedCiphertext)
                }
            }
            return plaintext.toString(Charsets.UTF_8)
        } catch (unsupported: UnsupportedBackupVersionException) {
            throw unsupported
        } catch (_: Exception) {
            throw BackupCryptoException()
        } finally {
            plaintext?.fill(0)
            salt?.fill(0)
            iv?.fill(0)
            ciphertext?.fill(0)
            authenticatedMetadata?.fill(0)
        }
    }

    fun decrypt(envelopeJson: String, password: String): String {
        val passwordBuffer = password.toCharArray()
        return try {
            decrypt(envelopeJson, passwordBuffer)
        } finally {
            passwordBuffer.fill('\u0000')
        }
    }

    /**
     * Reads structurally valid, non-secret metadata without attempting decryption. Authenticity is
     * established only when [decrypt] succeeds with the backup password.
     */
    fun inspect(envelopeJson: String): BackupMetadata = try {
        val envelope = parseAndValidate(envelopeJson)
        BackupMetadata(
            createdAt = Instant.parse(envelope.createdAt),
            version = envelope.version,
        )
    } catch (unsupported: UnsupportedBackupVersionException) {
        throw unsupported
    } catch (_: Exception) {
        throw BackupCryptoException()
    }

    private fun parseAndValidate(value: String): BackupEnvelope {
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(value)
        } catch (exception: SerializationException) {
            throw InvalidBackupException()
        } catch (exception: IllegalArgumentException) {
            throw InvalidBackupException()
        }

        if (
            envelope.format != FORMAT ||
            envelope.kdf.algorithm != KDF_ALGORITHM ||
            envelope.kdf.iterations != KDF_ITERATIONS ||
            envelope.cipher.algorithm != CIPHER_ALGORITHM
        ) {
            throw InvalidBackupException()
        }
        if (envelope.version != VERSION) throw UnsupportedBackupVersionException()

        try {
            Instant.parse(envelope.createdAt)
        } catch (exception: Exception) {
            throw InvalidBackupException()
        }

        return envelope
    }

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (exception: IllegalArgumentException) {
        throw InvalidBackupException()
    }

    private fun BackupEnvelope.toAuthenticatedHeader(): AuthenticatedHeader = AuthenticatedHeader(
        format = format,
        version = version,
        createdAt = createdAt,
        kdf = kdf,
        cipher = cipher,
    )

    private fun AuthenticatedHeader.toAuthenticatedBytes(): ByteArray =
        json.encodeToString(this).toByteArray(Charsets.UTF_8)

    private inline fun <T> withDerivedKey(
        password: CharArray,
        salt: ByteArray,
        block: (SecretKey) -> T,
    ): T {
        val keySpec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_SIZE_BITS)
        var derivedKey: ByteArray? = null
        var providerKey: SecretKey? = null
        return try {
            val generatedKey = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(keySpec)
            providerKey = generatedKey
            val encodedKey = generatedKey.encoded ?: throw InvalidBackupException()
            derivedKey = encodedKey
            block(SecretKeySpec(encodedKey, "AES"))
        } finally {
            keySpec.clearPassword()
            derivedKey?.fill(0)
            try {
                providerKey?.destroy()
            } catch (_: Exception) {
                // Some JCA providers cannot destroy their key object. The copied mutable key bytes
                // above are still cleared, and no reference is retained by this object.
            }
        }
    }

    @Serializable
    private data class BackupEnvelope(
        val format: String,
        val version: Int,
        val createdAt: String,
        val kdf: KdfEnvelope,
        val cipher: CipherEnvelope,
        val ciphertextBase64: String,
    )

    /** The canonical version-1 AES-GCM additional authenticated data. */
    @Serializable
    private data class AuthenticatedHeader(
        val format: String,
        val version: Int,
        val createdAt: String,
        val kdf: KdfEnvelope,
        val cipher: CipherEnvelope,
    )

    @Serializable
    private data class KdfEnvelope(
        val algorithm: String,
        val iterations: Int,
        val saltBase64: String,
    )

    @Serializable
    private data class CipherEnvelope(
        val algorithm: String,
        val ivBase64: String,
    )

    private class InvalidBackupException : Exception()
}

data class BackupMetadata(
    val createdAt: Instant,
    val version: Int,
)

class BackupCryptoException : Exception(BackupCrypto.DECRYPTION_ERROR_MESSAGE)

class UnsupportedBackupVersionException : Exception("Unsupported backup version")
