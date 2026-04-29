package com.project.blue_command.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import com.project.blue_command.model.CombatGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleBroadcastService(private val context: Context) : TacticalRadio {

    private val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    override val incomingCommands: SharedFlow<ByteArray> = BleDataBridge.incomingCommands

    // Pomocnicza funkcja do tworzenia PendingIntent
    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, BleScanReceiver::class.java).apply {
            action = "com.project.blue_command.ACTION_BLE_SCAN_RESULT"
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun startListening(group: CombatGroup) {
        val groupUuid = getGroupUuid(group)

        val filter = ScanFilter.Builder()
            .setServiceUuid(groupUuid)
            .setManufacturerData(0xFFFF, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, getPendingIntent())
            Log.d("BLE_PENDING", "Zlecono systemowe skanowanie dla grupy: ${group.name}")
        } catch (e: Exception) {
            Log.e("BLE_PENDING", "Nie udało się uruchomić skanowania systemowego", e)
        }
    }

    override fun stopListening() {
        try {
            scanner?.stopScan(getPendingIntent())
            Log.d("BLE_PENDING", "Zatrzymano skanowanie systemowe.")
        } catch (e: Exception) {
            Log.e("BLE_PENDING", "Błąd podczas zatrzymywania", e)
        }
    }

    private fun getGroupUuid(group: CombatGroup): ParcelUuid {
        val safeHex = String.format("%04X", group.id.hashCode() and 0xFFFF)
        return ParcelUuid(UUID.fromString("0000$safeHex-0000-1000-8000-00805f9b34fb"))
//        return ParcelUuid(UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb"))
    }

    override suspend fun broadcastCommand(group: CombatGroup, payload: ByteArray) {
        val groupUuid = getGroupUuid(group)
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