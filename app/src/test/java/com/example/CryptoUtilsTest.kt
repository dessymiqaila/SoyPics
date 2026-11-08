package com.example

import com.example.crypto.CryptoUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CryptoUtilsTest {

    @Test
    fun testEncryptionAndDecryptionRoundTrip() {
        val originalText = "SoyPics Secret Vault 2026 - Two Metallic Mountains"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)
        val password = "StrongMasterKey#89"

        val encryptedBytes = CryptoUtils.encryptBytes(plainBytes, password)
        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.size > plainBytes.size)

        val decryptedBytes = CryptoUtils.decryptBytes(encryptedBytes, password)
        assertArrayEquals(plainBytes, decryptedBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun testIncorrectPasswordRejection() {
        val plainBytes = "Confidential Information".toByteArray()
        val correctPassword = "CorrectPassword123"
        val wrongPassword = "WrongPassword456"

        val encryptedBytes = CryptoUtils.encryptBytes(plainBytes, correctPassword)

        assertTrue(CryptoUtils.verifyPassword(encryptedBytes, correctPassword))
        assertFalse(CryptoUtils.verifyPassword(encryptedBytes, wrongPassword))
    }

    @Test
    fun testStreamEncryptionDecryption() {
        val originalData = ByteArray(16384) { (it % 256).toByte() }
        val password = "StreamPasswordSecret"

        val encryptedOut = ByteArrayOutputStream()
        CryptoUtils.encryptStream(ByteArrayInputStream(originalData), encryptedOut, password)
        val cipherBytes = encryptedOut.toByteArray()
        assertTrue(cipherBytes.isNotEmpty())

        val decryptedBytes = CryptoUtils.decryptBytes(cipherBytes, password)
        assertArrayEquals(originalData, decryptedBytes)
    }
}
