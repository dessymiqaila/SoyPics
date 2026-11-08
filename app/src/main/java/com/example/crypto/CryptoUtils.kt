package com.example.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-security AES-256 GCM cryptographic utility with PBKDF2 key derivation.
 * Supports streaming and byte-array operations for the SoyPics gamelog.logz archive.
 */
object CryptoUtils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val ITERATION_COUNT = 65536
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    // Magic marker to verify format integrity
    val HEADER_MAGIC = byteArrayOf(0x53, 0x4F, 0x59, 0x5A) // "SOYZ"

    private val secureRandom = SecureRandom()

    /**
     * Derives a 256-bit AES SecretKey from the user's password and a random salt.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Encrypts plain byte array into an authenticated AES-256 GCM payload.
     * Output structure: [MAGIC 4B] + [SALT 16B] + [IV 12B] + [CIPHERTEXT + TAG]
     */
    fun encryptBytes(plainBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plainBytes)

        val output = ByteArrayOutputStream(HEADER_MAGIC.size + salt.size + iv.size + cipherBytes.size)
        output.write(HEADER_MAGIC)
        output.write(salt)
        output.write(iv)
        output.write(cipherBytes)
        return output.toByteArray()
    }

    /**
     * Decrypts an authenticated AES-256 GCM payload back into plain bytes.
     * Throws an exception if the password is incorrect or data is corrupted.
     */
    fun decryptBytes(encryptedBytes: ByteArray, password: String): ByteArray {
        require(encryptedBytes.size > HEADER_MAGIC.size + SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES) {
            "Payload too small or invalid archive format"
        }

        var offset = 0
        val magic = encryptedBytes.copyOfRange(offset, offset + HEADER_MAGIC.size)
        offset += HEADER_MAGIC.size

        // If magic matches, read standard format. Otherwise fallback for raw data
        val (salt, iv, cipherBytes) = if (magic.contentEquals(HEADER_MAGIC)) {
            val s = encryptedBytes.copyOfRange(offset, offset + SALT_LENGTH_BYTES)
            offset += SALT_LENGTH_BYTES
            val i = encryptedBytes.copyOfRange(offset, offset + GCM_IV_LENGTH_BYTES)
            offset += GCM_IV_LENGTH_BYTES
            val c = encryptedBytes.copyOfRange(offset, encryptedBytes.size)
            Triple(s, i, c)
        } else {
            // Raw header without magic
            val s = encryptedBytes.copyOfRange(0, SALT_LENGTH_BYTES)
            val i = encryptedBytes.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES)
            val c = encryptedBytes.copyOfRange(SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES, encryptedBytes.size)
            Triple(s, i, c)
        }

        val secretKey = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return cipher.doFinal(cipherBytes)
    }

    /**
     * Encrypts an InputStream and writes the authenticated ciphertext to an OutputStream.
     */
    fun encryptStream(input: InputStream, output: OutputStream, password: String) {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        output.write(HEADER_MAGIC)
        output.write(salt)
        output.write(iv)

        CipherOutputStream(output, cipher).use { cos ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                cos.write(buffer, 0, bytesRead)
            }
            cos.flush()
        }
    }

    /**
     * Decrypts encrypted bytes directly into an in-memory Android Bitmap without
     * writing plaintext to disk cache.
     */
    fun decryptToBitmap(encryptedBytes: ByteArray, password: String): Bitmap? {
        return try {
            val decryptedBytes = decryptBytes(encryptedBytes, password)
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verifies whether a given password correctly decrypts the payload without allocating full bitmap.
     */
    fun verifyPassword(sampleEncryptedBytes: ByteArray, password: String): Boolean {
        return try {
            decryptBytes(sampleEncryptedBytes, password)
            true
        } catch (e: Exception) {
            false
        }
    }
}
