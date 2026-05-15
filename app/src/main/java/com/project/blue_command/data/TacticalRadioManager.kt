package com.project.blue_command.data

import android.content.Context
import android.util.Log
import com.project.blue_command.data.ble.BleService
import com.project.blue_command.data.ble.BleServiceFactory
import com.project.blue_command.security.EncryptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

class TacticalRadioManager(
    context: Context,
    private val currentBleService: BleService = BleServiceFactory.getClassicBleBroadcastService(context)
) {
    private val encryptionManager = EncryptionManager()
    private val _currentMode = MutableStateFlow(RadioMode.CLASSIC_BLE)

    val incomingCommands: Flow<ByteArray> = currentBleService.incomingPayloads.mapNotNull { rawPayload ->
        val activeGroup = SessionRepository.activeGroup.value ?: return@mapNotNull null
        try {
            val decryptedBytes = encryptionManager.decryptPayload(rawPayload,
                encryptionManager.decodeKeyFromBase64(activeGroup.groupKeyBase64))
            decryptedBytes
        } catch (_ : Exception) {
            Log.d("BLE_ERROR", "Nie udało się odszyfrować odebranej komendy")
            null
        }
    }

    fun startListening() {
        currentBleService.startListening()
    }

    fun stopListening() {
        currentBleService.stopListening()
    }

    suspend fun sendCommand(payload: ByteArray) {
        val activeGroup = SessionRepository.activeGroup.value ?: return
        when (_currentMode.value) {
            RadioMode.CLASSIC_BLE -> {
                println("MANAGER: Wysyłka zaszyfrowanej komendy kluczem grupy ${activeGroup.name} przez Classic BLE")

                val keyBytes = encryptionManager.decodeKeyFromBase64(activeGroup.groupKeyBase64)

                val encrypted = encryptionManager.encryptPayload(payload, keyBytes)

                currentBleService.broadcastPayload(encrypted)
            }
        }
    }
}