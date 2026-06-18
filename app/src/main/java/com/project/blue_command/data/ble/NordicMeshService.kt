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

class NordicMeshService(
    private val meshRepository: MeshRepository,
    private val proxyConnection: MeshBleConnection,
    private val hardwareDetector: HardwareDetector
) : BleService {
    
    private val _incomingPayloads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incomingPayloads: SharedFlow<ByteArray> = _incomingPayloads.asSharedFlow()

    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    init {
        // Przekazywanie paczek przychodzących z silnika sieci mesh do aplikacji wyżej
        serviceScope.launch {
            meshRepository.incomingMessages.collect { payload ->
                _incomingPayloads.tryEmit(payload)
            }
        }
        
        // Zapisywanie wychodzących paczek PDU i przesyłanie ich fizycznym łączem Proxy In
        serviceScope.launch {
            meshRepository.outgoingPdu.collect { pdu ->
                proxyConnection.sendData(pdu)
            }
        }

        // Kiedy proxyConnection odbierze coś z Proxy Out, wstrzykujemy to do silnika mesh
        proxyConnection.setOnDataReceivedListener { data ->
            meshRepository.handleNotifications(data)
        }
    }

    override fun startListening() {
        if (isListening) return
        isListening = true
        Log.d("NordicMeshService", "Rozpoczynam nasłuchiwanie w sieci Nordic Mesh...")
        
        val state = hardwareDetector.deviceState.value
        if (state is MeshDeviceState.Proxy) {
            val targetMac = state.mac
            Log.d("NordicMeshService", "Zlecam połączenie GATT do płytki Mesh: $targetMac")
            proxyConnection.connectToProxy(targetMac)
        } else {
            Log.e("NordicMeshService", "Brak MAC adresu zdetektowanej płytki (stan: $state), nie można się połączyć.")
        }
    }

    override fun stopListening() {
        if (!isListening) return
        isListening = false
        Log.d("NordicMeshService", "Zatrzymuję nasłuchiwanie Nordic Mesh i rozłączam Proxy.")
        proxyConnection.disconnectProxy()
    }

    override suspend fun broadcastPayload(payload: ByteArray) {
        if (!isListening) {
            Log.w("NordicMeshService", "Nie można wysłać, usługa Mesh nie nasłuchuje.")
            return
        }
        // W testach Generic OnOff odczytujemy trzeci bajt payloadu (Command Code)
        // Zakładamy: parzyste = off (false), nieparzyste = on (true)
        val isOn = if (payload.size > 2) (payload[2].toInt() % 2 != 0) else true
        Log.d("NordicMeshService", "Zlecenie wysyłki SIG Generic OnOff przez MeshRepository. Stan: $isOn")
        meshRepository.sendGenericOnOffMessage(isOn)
    }
}
