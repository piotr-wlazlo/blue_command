package com.project.blue_command.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class EncryptionManager {
    private val keyString = "abcdefgh12345678"
    private val keySpec = SecretKeySpec(keyString.toByteArray(), "AES")
    private val transformation = "AES/CBC/PKCS5Padding"
    private val secretKey: Byte = 0x5A

    fun encryptToBytes(commandCode: Int): ByteArray {
        val encryptedByte = (commandCode.toByte().toInt() xor secretKey.toInt()).toByte()
        return byteArrayOf(encryptedByte)
    }

    fun decryptFromBytes(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val decryptedByte = (data[0].toInt() xor secretKey.toInt()).toByte()
        return decryptedByte.toInt()
    }

    fun encrypt(command: String): String {
        val cipher = Cipher.getInstance(transformation)

        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val encryptedBytes = cipher.doFinal(command.toByteArray())
        val combined = iv + encryptedBytes

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val iv = combined.sliceArray(0 until 16)
        val ivSpec = IvParameterSpec(iv)
        val encryptedData = combined.sliceArray(16 until combined.size)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decryptedBytes = cipher.doFinal(encryptedData)

        return String(decryptedBytes)
    }
}