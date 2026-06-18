package com.project.blue_command.data.ble

import android.util.Log
import com.project.blue_command.data.ble.mesh.MeshBleConnection
import com.project.blue_command.data.ble.mesh.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class NordicMeshProvisioningService(
    private val meshRepository: MeshRepository,
    private val provisioningConnection: MeshBleConnection,
    private val hardwareDetector: HardwareDetector
) : BleService {
    
    private val _incomingPayloads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incomingPayloads: SharedFlow<ByteArray> = _incomingPayloads.asSharedFlow()

    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var pendingUuidBytes: ByteArray? = null
    private var isDeviceReady = false

    init {
        serviceScope.launch {
            meshRepository.outgoingProvisioningPdu.collect { pdu ->
                provisioningConnection.sendData(pdu)
            }
        }

        provisioningConnection.setOnDataReceivedListener { data ->
            meshRepository.handleProvisioningNotifications(data)
        }
        
        provisioningConnection.setOnDeviceReadyListener {
            isDeviceReady = true
            pendingUuidBytes?.let { uuidBytes ->
                Log.i("NordicMeshProvService", "Urządzenie gotowe. Rozpoczynam wymianę kluczy dla węzła o UUID: ${uuidBytes.joinToString("") { "%02X".format(it) }}")
                meshRepository.startProvisioning(uuidBytes)
                pendingUuidBytes = null
            }
        }
    }

    override fun startListening() {
        if (isListening) return
        isListening = true
        isDeviceReady = false
        Log.d("NordicMeshProvService", "Rozpoczynam serwis Provisioning...")
        
        val state = hardwareDetector.deviceState.value
        if (state is MeshDeviceState.Unprovisioned) {
            Log.d("NordicMeshProvService", "Zlecam połączenie GATT (Provisioning) do: ${state.mac}")
            provisioningConnection.connectToProxy(state.mac)
        } else {
            Log.e("NordicMeshProvService", "Brak MAC adresu Unprovisioned Node.")
        }
    }

    override fun stopListening() {
        if (!isListening) return
        isListening = false
        isDeviceReady = false
        pendingUuidBytes = null
        Log.d("NordicMeshProvService", "Zatrzymuję serwis Provisioning i rozłączam GATT.")
        provisioningConnection.disconnectProxy()
    }

    override suspend fun broadcastPayload(payload: ByteArray) {
        Log.w("NordicMeshProvService", "Próba wysłania komendy taktycznej podczas fazy Provisioning. Ignoruję.")
    }

    fun acceptProvisioning(uuidBytes: ByteArray) {
        if (isDeviceReady) {
            Log.i("NordicMeshProvService", "Rozpoczynam wymianę kluczy dla węzła o UUID: ${uuidBytes.joinToString("") { "%02X".format(it) }}")
            meshRepository.startProvisioning(uuidBytes)
        } else {
            Log.i("NordicMeshProvService", "Oczekuję na gotowość GATT... Zapisuję UUID w kolejce.")
            pendingUuidBytes = uuidBytes
        }
    }
}
