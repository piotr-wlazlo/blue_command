package com.project.blue_command.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class MeshDeviceState {
    object None : MeshDeviceState()
    data class Proxy(val mac: String) : MeshDeviceState()
    data class Unprovisioned(val mac: String, val uuidBytes: ByteArray) : MeshDeviceState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Unprovisioned
            if (mac != other.mac) return false
            if (!uuidBytes.contentEquals(other.uuidBytes)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = mac.hashCode()
            result = 31 * result + uuidBytes.contentHashCode()
            return result
        }
    }
}

class HardwareDetector(private val context: Context) {
    // Standardowe UUID dla Mesh Proxy Service: 0x1828
    private val MESH_PROXY_UUID = ParcelUuid(UUID.fromString("00001828-0000-1000-8000-00805f9b34fb"))
    // Standardowe UUID dla Mesh Provisioning Service: 0x1827
    private val MESH_PROVISIONING_UUID = ParcelUuid(UUID.fromString("00001827-0000-1000-8000-00805f9b34fb"))

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private val _deviceState = MutableStateFlow<MeshDeviceState>(MeshDeviceState.None)
    val deviceState: StateFlow<MeshDeviceState> = _deviceState.asStateFlow()

    private var isScanning = false
    private var lastDetectionTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timeoutJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val serviceUuids = result.scanRecord?.serviceUuids
            
            val isProxy = serviceUuids?.contains(MESH_PROXY_UUID) == true
            val isUnprovisioned = serviceUuids?.contains(MESH_PROVISIONING_UUID) == true

            if (isProxy || isUnprovisioned) {
                lastDetectionTime = System.currentTimeMillis()
                val mac = result.device.address
                
                var newState: MeshDeviceState = MeshDeviceState.None
                if (isProxy) {
                    newState = MeshDeviceState.Proxy(mac)
                } else if (isUnprovisioned) {
                    val serviceData = result.scanRecord?.getServiceData(MESH_PROVISIONING_UUID)
                    if (serviceData != null && serviceData.size >= 16) {
                        // Pierwsze 16 bajtów to Device UUID
                        val uuidBytes = serviceData.copyOfRange(0, 16)
                        newState = MeshDeviceState.Unprovisioned(mac, uuidBytes)
                    } else {
                        // Awaryjnie, bez UUID
                        newState = MeshDeviceState.Unprovisioned(mac, ByteArray(16))
                    }
                }

                if (_deviceState.value != newState) {
                    Log.i("HardwareDetector", "Wykryto urządzenie Mesh: $newState")
                    _deviceState.value = newState
                }
                
                // Resetowanie licznika timeoutu
                timeoutJob?.cancel()
                timeoutJob = scope.launch {
                    delay(60000)
                    if (System.currentTimeMillis() - lastDetectionTime >= 60000) {
                        Log.i("HardwareDetector", "Utracono połączenie/zasięg ze sprzętem Mesh. Wracam do stanu None.")
                        _deviceState.value = MeshDeviceState.None
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("HardwareDetector", "Skanowanie BLE zakończone błędem: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startDetection() {
        if (isScanning) return
        val currentScanner = bluetoothAdapter?.bluetoothLeScanner
        if (currentScanner == null) {
            Log.w("HardwareDetector", "Skaner BLE jest niedostępny. Upewnij się, że Bluetooth jest włączony i nadano uprawnienia lokalizacji/BLE.")
            return
        }

        val filterProxy = ScanFilter.Builder().setServiceUuid(MESH_PROXY_UUID).build()
        val filterProvisioning = ScanFilter.Builder().setServiceUuid(MESH_PROVISIONING_UUID).build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            currentScanner.startScan(listOf(filterProxy, filterProvisioning), settings, scanCallback)
            isScanning = true
            Log.d("HardwareDetector", "Rozpoczęto skanowanie w poszukiwaniu sprzętu Nordic Mesh (1827/1828)...")
        } catch (e: Exception) {
            Log.e("HardwareDetector", "Nie udało się uruchomić skanera BLE", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDetection() {
        if (!isScanning) return
        val currentScanner = bluetoothAdapter?.bluetoothLeScanner
        if (currentScanner == null) {
            isScanning = false
            return
        }

        try {
            currentScanner.stopScan(scanCallback)
            isScanning = false
            Log.d("HardwareDetector", "Zatrzymano skanowanie sprzętu Nordic Mesh.")
            _deviceState.value = MeshDeviceState.None
        } catch (e: Exception) {
            Log.e("HardwareDetector", "Nie udało się zatrzymać skanera BLE", e)
        }
    }
}
