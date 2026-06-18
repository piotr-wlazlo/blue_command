package com.project.blue_command.data.ble.mesh

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import java.util.UUID

/**
 * Menedżer BLE odpowiedzialny za fizyczne łączenie GATT do urządzenia będącego w stanie
 * Unprovisioned Node. Poszukuje usługi Mesh Provisioning (1827).
 */
class BleMeshProvisioningManager(context: Context) : BleManager(context), MeshBleConnection {

    private val MESH_PROVISIONING_UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb")
    private val MESH_PROVISIONING_DATA_IN = UUID.fromString("00002add-0000-1000-8000-00805f9b34fb")
    private val MESH_PROVISIONING_DATA_OUT = UUID.fromString("00002adc-0000-1000-8000-00805f9b34fb")

    private var meshProvisioningDataInCharacteristic: BluetoothGattCharacteristic? = null
    private var meshProvisioningDataOutCharacteristic: BluetoothGattCharacteristic? = null

    private var onDataReceivedListener: ((ByteArray) -> Unit)? = null
    private var onDeviceReadyListener: (() -> Unit)? = null

    override fun connectToProxy(macAddress: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null) {
            Log.e("BleMeshProvisioningMgr", "Brak wsparcia dla Bluetooth w urządzeniu")
            return
        }
        
        try {
            val device = adapter.getRemoteDevice(macAddress)
            connect(device)
                .retry(3, 100)
                .useAutoConnect(false)
                .enqueue()
        } catch (e: Exception) {
            Log.e("BleMeshProvisioningMgr", "Nie udało się nawiązać połączenia z MAC $macAddress: ${e.message}")
        }
    }

    override fun disconnectProxy() {
        disconnect().enqueue()
    }

    override fun setOnDataReceivedListener(listener: (ByteArray) -> Unit) {
        onDataReceivedListener = listener
    }

    override fun setOnDeviceReadyListener(listener: () -> Unit) {
        onDeviceReadyListener = listener
    }

    override fun sendData(data: ByteArray) {
        val char = meshProvisioningDataInCharacteristic
        if (char == null) {
            Log.e("BleMeshProvisioningMgr", "Brak charakterystyki Data In, nie można wysłać PDU.")
            return
        }

        writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .with { _, _ -> Log.d("BleMeshProvisioningMgr", "Wysłano pakiet do węzła w trakcie provisioningu: ${data.size} bajtów") }
            .enqueue()
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(MESH_PROVISIONING_UUID)
                if (service != null) {
                    meshProvisioningDataInCharacteristic = service.getCharacteristic(MESH_PROVISIONING_DATA_IN)
                    meshProvisioningDataOutCharacteristic = service.getCharacteristic(MESH_PROVISIONING_DATA_OUT)
                }

                val supported = meshProvisioningDataInCharacteristic != null &&
                        meshProvisioningDataOutCharacteristic != null

                if (!supported) {
                    Log.w("BleMeshProvisioningMgr", "Serwis 1827 lub jego charakterystyki nie zostały znalezione!")
                }
                return supported
            }

            override fun initialize() {
                val charOut = meshProvisioningDataOutCharacteristic ?: return
                
                setNotificationCallback(charOut).with { _, data ->
                    val value = data.value
                    if (value != null) {
                        Log.d("BleMeshProvisioningMgr", "Odebrano z Provisioning Out: ${value.size} bajtów")
                        onDataReceivedListener?.invoke(value)
                    }
                }
                enableNotifications(charOut).enqueue()
            }

            override fun onDeviceReady() {
                super.onDeviceReady()
                Log.i("BleMeshProvisioningMgr", "Urządzenie gotowe do komunikacji!")
                onDeviceReadyListener?.invoke()
            }

            override fun onDeviceDisconnected() {
                meshProvisioningDataInCharacteristic = null
                meshProvisioningDataOutCharacteristic = null
            }
            
            override fun onServicesInvalidated() {
                meshProvisioningDataInCharacteristic = null
                meshProvisioningDataOutCharacteristic = null
            }
        }
    }
}
