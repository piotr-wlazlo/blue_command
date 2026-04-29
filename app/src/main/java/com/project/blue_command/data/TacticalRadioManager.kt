package com.project.blue_command.data

import android.content.Context
import android.util.Log
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.security.EncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TacticalRadioManager(context: Context) : TacticalRadio {
    private val classicBleService = BleBroadcastService(context)

    // Pozniej bedzie implementacja nordic dla płytek
//    private val nordicMeshService = NordicBleMeshService(context)
    private val encryptionManager = EncryptionManager()

    private val _currentMode = MutableStateFlow(RadioMode.CLASSIC_BLE)
//    val currentMode: StateFlow<RadioMode> = _currentMode

    private val _incomingCommands = MutableSharedFlow<ByteArray>(extraBufferCapacity = 20)
    override val incomingCommands: SharedFlow<ByteArray> = _incomingCommands

    init {
        CoroutineScope(Dispatchers.IO).launch {
            launch {
                classicBleService.incomingCommands.collect { encryptedPayload ->
                    val currentGroup = SessionRepository.activeGroup.value ?: return@collect
                    val keyBytes =
                        encryptionManager.decodeKeyFromBase64(currentGroup.groupKeyBase64)

                    // Wypisuje klucz w formacie tablicy C++ do płytki w celu testowania
                    val keyHexForC = keyBytes.joinToString(", ") { String.format("0x%02X", it) }
                    Log.d("BLE_KRYPTO", "==> KLUCZ GRUPY W FORMIE TABLICY BAJTÓW: { $keyHexForC };")

                    val decrypted = encryptionManager.decryptPayload(encryptedPayload, keyBytes)
                    if (decrypted != null) _incomingCommands.tryEmit(decrypted)
                }
            }
        }
    }

    // Funkcja do zmieniania łaczności BLE na BLE Mesh
//    fun switchMode(newMode: RadioMode, activeGroup: CombatGroup?) {
//        if (_currentMode.value == newMode) return
//
//        println("RADIO MANAGER: Przełączam tryb na $newMode")
//
//        stopListening()
//
//        _currentMode.value = newMode
//
//        if (activeGroup != null) {
//            startListening(activeGroup)
//        }
//    }

    override fun startListening(group: CombatGroup) {
        classicBleService.startListening(group)
    }

    override fun stopListening() {
        classicBleService.stopListening()
    }

    override suspend fun broadcastCommand(group: CombatGroup, payload: ByteArray) {
        when (_currentMode.value) {
            RadioMode.CLASSIC_BLE -> {
                println("MANAGER: Wysyłka zaszyfrowanej komendy kluczem grupy ${group.name} przez Classic BLE")

                val keyBytes = encryptionManager.decodeKeyFromBase64(group.groupKeyBase64)

                val encrypted = encryptionManager.encryptPayload(payload, keyBytes)

                classicBleService.broadcastCommand(group, encrypted)
            }
        }
    }
}