package com.project.blue_command.logic

import androidx.lifecycle.ViewModel
import com.project.blue_command.model.Command
import com.project.blue_command.security.EncryptionManager

class CommandController: ViewModel() {
    private val encryptionManager = EncryptionManager()

    fun onCommandSelected(command: Command) {
        val rawCommand = "${command.code}, ${command.label}"
        val encryptedCommand = encryptionManager.encrypt(rawCommand)
        val decryptedCommand = encryptionManager.decrypt(encryptedCommand)

        println("pressed: ${command.code}, ${command.label}")
        println("raw: ${rawCommand}")
        println("encrypted: ${encryptedCommand}")
        println("decrypted: ${decryptedCommand}")
    }
}