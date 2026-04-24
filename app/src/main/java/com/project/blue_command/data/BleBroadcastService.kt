package com.project.blue_command.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleBroadcastService(context: Context) : TacticalRadio {

    private val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private val _incomingMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    override val incomingMessages: SharedFlow<ByteArray> = _incomingMessages

    private var activeScanCallback: ScanCallback? = null

    private fun getGroupUuid(groupId: String): ParcelUuid {
        val safeHex = String.format("%04X", groupId.hashCode() and 0xFFFF)
        return ParcelUuid(UUID.fromString("0000$safeHex-0000-1000-8000-00805f9b34fb"))
    }

    override fun startListening(groupId: String) {
        stopListening()
        val groupUuid = getGroupUuid(groupId)

        val filter = ScanFilter.Builder().setServiceUuid(groupUuid).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        activeScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val data = result.scanRecord?.getServiceData(groupUuid)
                if (data != null) _incomingMessages.tryEmit(data)
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, activeScanCallback)
            println("BLE ODBIORNIK: Rozpoczęto fizyczne nasłuchiwanie dla grupy: $groupId")
        } catch (exception: SecurityException) {
            activeScanCallback = null
            println("BLE ODBIORNIK: Brak wymaganych uprawnien Bluetooth do nasluchu.")
        } catch (exception: IllegalStateException) {
            activeScanCallback = null
            println("BLE ODBIORNIK: Nie mozna uruchomic nasluchu BLE w tym stanie urzadzenia.")
        }
    }

    override fun stopListening() {
        activeScanCallback?.let {
            try {
                scanner?.stopScan(it)
                println("BLE ODBIORNIK: Zatrzymano nasluchiwanie.")
            } catch (exception: SecurityException) {
                println("BLE ODBIORNIK: Brak wymaganych uprawnien Bluetooth do zatrzymania nasluchu.")
            } finally {
                activeScanCallback = null
            }
        }
    }

    override suspend fun broadcastCommand(groupId: String, payload: ByteArray) {
        val groupUuid = getGroupUuid(groupId)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(groupUuid)
            .addServiceData(groupUuid, payload)
            .build()

        val callback = object : AdvertiseCallback() {}

        try {
            println("BLE NADAJNIK: Rozpoczeto fizyczne nadawanie.")
            advertiser?.startAdvertising(settings, data, callback)
            delay(2000)
            advertiser?.stopAdvertising(callback)
            println("BLE NADAJNIK: Zakonczono nadawanie.")
        } catch (exception: SecurityException) {
            println("BLE NADAJNIK: Brak wymaganych uprawnien Bluetooth do nadawania.")
        } catch (exception: IllegalStateException) {
            println("BLE NADAJNIK: Nie mozna uruchomic nadawania BLE w tym stanie urzadzenia.")
        }
    }
}