package com.project.blue_command.security

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManager {
    private val transformation = "AES/CTR/NoPadding"
    private val nonceSize = 8

    fun generateNewGroupKeyBase64(): String {
        val keyBytes = ByteArray(16)
        SecureRandom().nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    fun decodeKeyFromBase64(base64Key: String): ByteArray {
        return Base64.decode(base64Key, Base64.NO_WRAP)
    }

    fun encryptPayload(data: ByteArray, keyBytes: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(transformation)

        val nonce = ByteArray(nonceSize)
        SecureRandom().nextBytes(nonce)

        val fullIv = ByteArray(16)
        System.arraycopy(nonce, 0, fullIv, 0, nonceSize)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(fullIv))
        val encryptedData = cipher.doFinal(data)

        return nonce + encryptedData
    }

    fun decryptPayload(data: ByteArray, keyBytes: ByteArray): ByteArray? {
        return try {
            if (data.size <= nonceSize) {
                Log.e("INCORRECT_DECRYPTION", "Niezgodność długości Nonce z długością danych")
                return null
            }

            val nonce = data.sliceArray(0 until nonceSize)
            val encryptedData = data.sliceArray(nonceSize until data.size)

            val fullIv = ByteArray(16)
            System.arraycopy(nonce, 0, fullIv, 0, nonceSize)

            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(fullIv))

            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e("INCORRECT_DECRYPTION", "Nieudana próba deszyfrowania nagłówku payload")
            null
        }
    }
}