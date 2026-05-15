package com.project.blue_command.data.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@SuppressLint("MissingPermission")
class BleBroadcastService(private val context: Context
) : BleService {

    private val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    override val incomingPayloads: SharedFlow<ByteArray> = BleDataBridge.incomingPayloads

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, BleScanReceiver::class.java).apply {
            action = "com.project.blue_command.ACTION_BLE_SCAN_RESULT"
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    // Zmiana podejscia - nasluchujemy wszystkiego co jest zgodne z manufacturerData
    // Uwzglednienie uuid grup powoduje przekraczanie limitu pakietow w BLE czyli 31 bajtow
    // Na ten moment stosujemy tylko manufacturer, a rozroznianie grup bedzie w commandcontroller
    override fun startListening() {
        val filter = ScanFilter.Builder()
            .setManufacturerData(0xFFFF, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, getPendingIntent())
            Log.d("BLE_SCAN", "Rozpoczęto skanowanie dla dowolnych wiadomości zgodnych z ManufacturerData")
        } catch (e: Exception) {
            Log.e("BLE_ERROR", "Nie udało się uruchomić skanowania", e)
        }
    }

    override fun stopListening() {
        try {
            scanner?.stopScan(getPendingIntent())
            Log.d("BLE_SCAN", "Zatrzymano skanowanie systemowe.")
        } catch (e: Exception) {
            Log.e("BLE_ERROR", "Błąd podczas zatrzymywania", e)
        }
    }

    // Nie uwzgledniam grupy, bo zdeszyfrowany komunikat jest wystarczajacą odpowiedzią
    // czy komenda zostala nadana przez kogos z tej samej grupy
    override suspend fun broadcastPayload(payload: ByteArray) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0xFFFF, payload)
            .build()

        val callback = object : AdvertiseCallback() {}

        try {
            println("BLE ADVERTISE: Rozpoczeto fizyczne nadawanie.")
            advertiser?.startAdvertising(settings, data, callback)
            delay(2000)
            advertiser?.stopAdvertising(callback)
            println("BLE ADVERTISE: Zakonczono nadawanie.")
        } catch (_: SecurityException) {
            println("BLE ADVERTISE: Brak wymaganych uprawnien Bluetooth do nadawania.")
        } catch (_: IllegalStateException) {
            println("BLE ADVERTISE: Nie mozna uruchomic nadawania BLE w tym stanie urzadzenia.")
        }
    }
}