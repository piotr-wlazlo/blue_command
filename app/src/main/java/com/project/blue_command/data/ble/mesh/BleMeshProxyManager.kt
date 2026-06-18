package com.project.blue_command.data.ble.mesh

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import java.util.UUID

class BleMeshProxyManager(private val context: Context) : BleManager(context), MeshBleConnection {

    private var proxyDataInCharacteristic: BluetoothGattCharacteristic? = null
    private var proxyDataOutCharacteristic: BluetoothGattCharacteristic? = null
    private var onDataReceivedListener: ((ByteArray) -> Unit)? = null
    private var onDeviceReadyListener: (() -> Unit)? = null

    companion object {
        val MESH_PROXY_UUID: UUID = UUID.fromString("00001828-0000-1000-8000-00805f9b34fb")
        val MESH_PROXY_DATA_IN: UUID = UUID.fromString("00002add-0000-1000-8000-00805f9b34fb")
        val MESH_PROXY_DATA_OUT: UUID = UUID.fromString("00002ade-0000-1000-8000-00805f9b34fb")
    }

    override fun log(priority: Int, message: String) {
        Log.println(priority, "BleMeshProxyManager", message)
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(MESH_PROXY_UUID)
                if (service != null) {
                    proxyDataInCharacteristic = service.getCharacteristic(MESH_PROXY_DATA_IN)
                    proxyDataOutCharacteristic = service.getCharacteristic(MESH_PROXY_DATA_OUT)
                }
                
                var writeRequest = false
                var writeCommand = false
                proxyDataInCharacteristic?.let {
                    val properties = it.properties
                    writeRequest = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0
                    writeCommand = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0
                }
                return proxyDataInCharacteristic != null && proxyDataOutCharacteristic != null && (writeRequest || writeCommand)
            }

            override fun initialize() {
                val charOut = proxyDataOutCharacteristic ?: return
                setNotificationCallback(charOut).with { _, data ->
                    val value = data.value
                    if (value != null) {
                        onDataReceivedListener?.invoke(value)
                    }
                }
                enableNotifications(charOut).enqueue()
            }

            override fun onDeviceReady() {
                super.onDeviceReady()
                Log.i("BleMeshProxyManager", "Urządzenie gotowe do komunikacji!")
                onDeviceReadyListener?.invoke()
            }

            override fun onServicesInvalidated() {
                proxyDataInCharacteristic = null
                proxyDataOutCharacteristic = null
            }

            override fun onDeviceDisconnected() {
                proxyDataInCharacteristic = null
                proxyDataOutCharacteristic = null
            }
        }
    }

    override fun connectToProxy(macAddress: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null) {
            Log.e("BleMeshProxyManager", "Brak wsparcia dla Bluetooth w urządzeniu")
            return
        }

        try {
            val device = adapter.getRemoteDevice(macAddress)
            connect(device)
                .retry(3, 100)
                .useAutoConnect(true)
                .enqueue()
        } catch (e: Exception) {
            Log.e("BleMeshProxyManager", "Nie udało się nawiązać połączenia z MAC $macAddress: ${e.message}")
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
        proxyDataInCharacteristic?.let { char ->
            writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                .split() // Biblioteka Nordic sama podzieli na paczki po MTU (domyślnie 20)
                .enqueue()
        } ?: Log.e("BleMeshProxyManager", "Nie można wysłać danych. Charakterystyka In jest null.")
    }
}
