package com.project.blue_command.data

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BleScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (errorCode != 0) {
            Log.e("BLE_PENDING", "Błąd skanowania systemowego: $errorCode")
            return
        }

        val results: ArrayList<ScanResult>? = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)

        results?.forEach { result ->
            val scanRecord = result.scanRecord ?: return@forEach
            val manufacturerData = scanRecord.getManufacturerSpecificData(0xFFFF)

            if (manufacturerData != null) {
                Log.d("BLE_PENDING", "SYSTEM WYKRYŁ ESP32! Przekazuję dane...")
                BleDataBridge.emit(manufacturerData)
            }
        }
    }
}