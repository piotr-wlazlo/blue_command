package com.project.blue_command.data

import android.content.Context
import android.util.Log
import com.project.blue_command.data.ble.BleService
import com.project.blue_command.data.ble.BleServiceFactory
import com.project.blue_command.data.ble.HardwareDetector
import com.project.blue_command.data.ble.NordicMeshService
import com.project.blue_command.security.EncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

class TacticalRadioManager(
    private val context: Context,
    private val hardwareDetector: HardwareDetector = HardwareDetector(context),
    private var classicBleService: BleService = BleServiceFactory.getClassicBleBroadcastService(context),
    private val nordicMeshService: BleService = BleServiceFactory.getNordicMeshService(context, hardwareDetector),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val encryptionManager = EncryptionManager()
    private val _currentMode = MutableStateFlow(RadioMode.CLASSIC_BLE)
    
    private var isManagerListening = false

    private val provisioningService = com.project.blue_command.data.ble.NordicMeshProvisioningService(
        com.project.blue_command.data.ble.mesh.MeshRepository(context),
        com.project.blue_command.data.ble.mesh.BleMeshProvisioningManager(context),
        hardwareDetector
    )

    init {
        // Obserwacja detektora sprzętu w tle
        coroutineScope.launch {
            hardwareDetector.deviceState.collect { state ->
                val newMode = when (state) {
                    is com.project.blue_command.data.ble.MeshDeviceState.Proxy -> RadioMode.NORDIC_MESH
                    is com.project.blue_command.data.ble.MeshDeviceState.Unprovisioned -> RadioMode.PROVISIONING_MESH
                    else -> RadioMode.CLASSIC_BLE
                }
                
                if (_currentMode.value != newMode) {
                    // Jeśli ogólny manager nasłuchuje, trzeba przełączyć aktywne serwisy
                    if (isManagerListening) {
                        getActiveService(_currentMode.value).stopListening()
                        getActiveService(newMode).startListening()
                    }
                    _currentMode.value = newMode

                    if (state is com.project.blue_command.data.ble.MeshDeviceState.Unprovisioned && isManagerListening) {
                        Log.i("TacticalRadioManager", "Znalazłem czystą płytkę! Automatycznie wysyłam zgodę na provisioning...")
                        provisioningService.acceptProvisioning(state.uuidBytes)
                    }
                }
            }
        }
    }

    private fun getActiveService(mode: RadioMode): BleService {
        return when (mode) {
            RadioMode.NORDIC_MESH -> nordicMeshService
            RadioMode.PROVISIONING_MESH -> provisioningService
            else -> classicBleService
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomingCommands: Flow<ByteArray> = _currentMode.flatMapLatest { mode ->
        getActiveService(mode).incomingPayloads
    }.mapNotNull { rawPayload ->
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
        if (isManagerListening) return
        isManagerListening = true
        hardwareDetector.startDetection()
        getActiveService(_currentMode.value).startListening()
    }

    fun stopListening() {
        if (!isManagerListening) return
        isManagerListening = false
        hardwareDetector.stopDetection()
        getActiveService(_currentMode.value).stopListening()
    }

    suspend fun sendCommand(payload: ByteArray) {
        val activeGroup = SessionRepository.activeGroup.value ?: return
        val currentMode = _currentMode.value
        
        println("MANAGER: Wysyłka zaszyfrowanej komendy kluczem grupy ${activeGroup.name} przez $currentMode")

        val keyBytes = encryptionManager.decodeKeyFromBase64(activeGroup.groupKeyBase64)
        val encrypted = encryptionManager.encryptPayload(payload, keyBytes)

        getActiveService(currentMode).broadcastPayload(encrypted)
    }
}