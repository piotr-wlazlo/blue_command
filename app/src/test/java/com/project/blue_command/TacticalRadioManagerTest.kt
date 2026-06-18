package com.project.blue_command

import android.content.Context
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.TacticalRadioManager
import com.project.blue_command.data.ble.BleService
import com.project.blue_command.data.ble.HardwareDetector
import com.project.blue_command.model.CombatGroup
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TacticalRadioManagerTest {

    @Before
    fun setUp() {
        val curGroup = CombatGroup(
            id = "group1", name = "Alfa", memberIds = mutableListOf("user1"), groupKeyBase64 = "123"
        )
        SessionRepository.setActiveGroup(curGroup)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `default mode is CLASSIC_BLE and starts classic service when listening`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockClassicBleService = mockk<BleService>(relaxed = true)
        val mockNordicMeshService = mockk<BleService>(relaxed = true)
        val hardwareDetectionState = MutableStateFlow<com.project.blue_command.data.ble.MeshDeviceState>(com.project.blue_command.data.ble.MeshDeviceState.None)
        val mockHardwareDetector = mockk<HardwareDetector>(relaxed = true) {
            every { deviceState } returns hardwareDetectionState
        }

        val tacticalRadioManager = TacticalRadioManager(
            context = mockContext,
            classicBleService = mockClassicBleService,
            nordicMeshService = mockNordicMeshService,
            hardwareDetector = mockHardwareDetector,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        tacticalRadioManager.startListening()
        advanceUntilIdle()

        verify(exactly = 1) { mockHardwareDetector.startDetection() }
        verify(exactly = 1) { mockClassicBleService.startListening() }
        verify(exactly = 0) { mockNordicMeshService.startListening() }
    }

    @Test
    fun `detecting mesh hardware stops classic service and starts nordic mesh service`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockClassicBleService = mockk<BleService>(relaxed = true)
        val mockNordicMeshService = mockk<BleService>(relaxed = true)
        val hardwareDetectionState = MutableStateFlow<com.project.blue_command.data.ble.MeshDeviceState>(com.project.blue_command.data.ble.MeshDeviceState.None)
        val mockHardwareDetector = mockk<HardwareDetector>(relaxed = true) {
            every { deviceState } returns hardwareDetectionState
        }

        val tacticalRadioManager = TacticalRadioManager(
            context = mockContext,
            classicBleService = mockClassicBleService,
            nordicMeshService = mockNordicMeshService,
            hardwareDetector = mockHardwareDetector,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        tacticalRadioManager.startListening()
        advanceUntilIdle()

        // Wykrycie sprzętu
        hardwareDetectionState.value = com.project.blue_command.data.ble.MeshDeviceState.Proxy("AA:BB")
        advanceUntilIdle()

        // Klasyczne radio zostaje wyłączone
        verify(exactly = 1) { mockClassicBleService.stopListening() }
        // Nordic Mesh radio zostaje załączone
        verify(exactly = 1) { mockNordicMeshService.startListening() }
    }

    @Test
    fun `losing mesh hardware stops nordic mesh service and reverts to classic service`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockClassicBleService = mockk<BleService>(relaxed = true)
        val mockNordicMeshService = mockk<BleService>(relaxed = true)
        val hardwareDetectionState = MutableStateFlow<com.project.blue_command.data.ble.MeshDeviceState>(com.project.blue_command.data.ble.MeshDeviceState.None)
        val mockHardwareDetector = mockk<HardwareDetector>(relaxed = true) {
            every { deviceState } returns hardwareDetectionState
        }

        val tacticalRadioManager = TacticalRadioManager(
            context = mockContext,
            classicBleService = mockClassicBleService,
            nordicMeshService = mockNordicMeshService,
            hardwareDetector = mockHardwareDetector,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        tacticalRadioManager.startListening()
        advanceUntilIdle()

        // Wykrycie
        hardwareDetectionState.value = com.project.blue_command.data.ble.MeshDeviceState.Proxy("AA:BB")
        advanceUntilIdle()

        // Utrata połączenia (np. po 10 sekundowym timeoutcie)
        hardwareDetectionState.value = com.project.blue_command.data.ble.MeshDeviceState.None
        advanceUntilIdle()

        // Nordic mesh service is stopped
        verify(exactly = 1) { mockNordicMeshService.stopListening() }
        // Start called twice for classic: once initially, once after returning
        verify(exactly = 2) { mockClassicBleService.startListening() }
    }
}
