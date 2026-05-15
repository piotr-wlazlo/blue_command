package com.project.blue_command

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import com.project.blue_command.data.ble.BleBroadcastService
import com.project.blue_command.data.ble.BleDataBridge
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BleBroadcastServiceTest {

    private lateinit var context: Context
    private val mockBluetoothManager: BluetoothManager = mockk(relaxed = true)
    private val bleAdapter: BluetoothAdapter = mockk(relaxed = true)
    private val bleAdvertiser: BluetoothLeAdvertiser = mockk(relaxed = true)
    private val bleScanner: BluetoothLeScanner = mockk(relaxed = true)
    private val pendingIntent: PendingIntent = mockk(relaxed = true)

    private lateinit var bleBroadcastService: BleBroadcastService

    @Before
    fun setUp() {
        context = mockk(relaxed = true)

        every { context.getSystemService(BluetoothManager::class.java) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns bleAdapter
        every { bleAdapter.bluetoothLeAdvertiser } returns bleAdvertiser
        every { bleAdapter.bluetoothLeScanner } returns bleScanner

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)

        mockkConstructor(AdvertiseSettings.Builder::class)
        mockkConstructor(AdvertiseData.Builder::class)
        mockkConstructor(ScanFilter.Builder::class)
        mockkConstructor(ScanSettings.Builder::class)

        val advSettingsBuilder: AdvertiseSettings.Builder = mockk(relaxed = true)
        val advertiseSettings: AdvertiseSettings = mockk(relaxed = true)
        every { anyConstructed<AdvertiseSettings.Builder>().setAdvertiseMode(any()) } returns advSettingsBuilder
        every { anyConstructed<AdvertiseSettings.Builder>().setTxPowerLevel(any()) } returns advSettingsBuilder
        every { anyConstructed<AdvertiseSettings.Builder>().setConnectable(any()) } returns advSettingsBuilder
        every { anyConstructed<AdvertiseSettings.Builder>().build() } returns advertiseSettings

        val dataBuilder: AdvertiseData.Builder = mockk(relaxed = true)
        val advertiseData: AdvertiseData = mockk(relaxed = true)
        every { anyConstructed<AdvertiseData.Builder>().addManufacturerData(any(), any()) } returns dataBuilder
        every { anyConstructed<AdvertiseData.Builder>().build() } returns advertiseData

        val scanFilterBuilder: ScanFilter.Builder = mockk(relaxed = true)
        val scanFilter: ScanFilter = mockk(relaxed = true)
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } returns scanFilterBuilder
        every { anyConstructed<ScanFilter.Builder>().build() } returns scanFilter

        val scanSettingsBuilder: ScanSettings.Builder = mockk(relaxed = true)
        val scanSettings: ScanSettings = mockk(relaxed = true)
        every { anyConstructed<ScanSettings.Builder>().setScanMode(any()) } returns scanSettingsBuilder
        every { anyConstructed<ScanSettings.Builder>().build() } returns scanSettings

        bleBroadcastService = BleBroadcastService(context)
    }

    @Test
    fun `startListening calls scanner startScan with correct filter and settings`() {
        val filterSlot = slot<List<ScanFilter>>()
        val settingsSlot = slot<ScanSettings>()

        every {
            bleScanner.startScan(capture(filterSlot), capture(settingsSlot), any<PendingIntent>())
        }

        bleBroadcastService.startListening()

        verify(exactly = 1) {
            bleScanner.startScan(any(), any(), any<PendingIntent>())
        }

        verify {
            anyConstructed<ScanSettings.Builder>().setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            anyConstructed<ScanFilter.Builder>().setManufacturerData(0xFFFF, byteArrayOf())
            bleScanner.startScan(capture(filterSlot),capture(settingsSlot),any<PendingIntent>())
        }
    }

    @Test
    fun `startListening does nothing when scanner is null`() {
        every { bleAdapter.bluetoothLeScanner } returns null
        val serviceWithNullScanner = BleBroadcastService(context)

        serviceWithNullScanner.startListening()

        verify(exactly = 0) {
            bleScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<PendingIntent>())
        }
    }

    @Test
    fun `stopListening calls scanner stopScan`() {
        every { bleScanner.stopScan(any<PendingIntent>()) } just runs

        bleBroadcastService.stopListening()

        verify(exactly = 1) { bleScanner.stopScan(any<PendingIntent>()) }
        verify(atLeast = 1) { Log.d(any(), any()) }
    }

    @Test
    fun `stopListening does nothing when scanner is null`() {
        every { bleAdapter.bluetoothLeScanner } returns null
        val serviceWithNullScanner = BleBroadcastService(context)

        serviceWithNullScanner.stopListening()

        verify(exactly = 0) { bleScanner.stopScan(any<PendingIntent>()) }
    }

    @Test
    fun `broadcastPayload starts and stops advertising`() = runTest {
        every { bleAdvertiser.startAdvertising(any(), any(), any()) } just runs
        every { bleAdvertiser.stopAdvertising(any()) } just runs

        bleBroadcastService.broadcastPayload(byteArrayOf(0x01, 0x02, 0x03))

        verifyOrder {
            bleAdvertiser.startAdvertising(any(), any(), any())
            bleAdvertiser.stopAdvertising(any())
        }
    }

    @Test
    fun `broadcastPayload does nothing when advertiser is null`() = runTest {
        every { bleAdapter.bluetoothLeAdvertiser } returns null
        val serviceWithNullAdvertiser = BleBroadcastService(context)

        serviceWithNullAdvertiser.broadcastPayload(byteArrayOf(0x01))

        verify(exactly = 0) { bleAdvertiser.startAdvertising(any(), any(), any()) }
        verify(exactly = 0) { bleAdvertiser.stopAdvertising(any()) }
    }

    @Test
    fun `broadcastPayload catches SecurityException and does not propagate`() = runTest {
        every {
            bleAdvertiser.startAdvertising(any(), any(), any())
        } throws SecurityException("No permission")

        bleBroadcastService.broadcastPayload(byteArrayOf(0x01))

        verify(exactly = 0) { bleAdvertiser.stopAdvertising(any()) }
    }

    @Test
    fun `broadcastPayload catches IllegalStateException and does not propagate`() = runTest {
        every {
            bleAdvertiser.startAdvertising(any(), any(), any())
        } throws IllegalStateException("BT off")

        bleBroadcastService.broadcastPayload(byteArrayOf(0x01))

        verify(exactly = 0) { bleAdvertiser.stopAdvertising(any()) }
    }

    @Test
    fun `incomingPayloads is backed by BleDataBridge`() = runTest {
        val testPayload = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        var received: ByteArray? = null

        val job = launch(Dispatchers.Unconfined) {
            received = bleBroadcastService.incomingPayloads.first()
        }

        BleDataBridge.emit(testPayload)
        job.join()

        assertArrayEquals(testPayload, received)
    }
}