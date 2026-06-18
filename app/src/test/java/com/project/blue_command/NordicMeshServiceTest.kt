package com.project.blue_command

import com.project.blue_command.data.ble.HardwareDetector
import com.project.blue_command.data.ble.MeshDeviceState
import com.project.blue_command.data.ble.NordicMeshService
import com.project.blue_command.data.ble.mesh.MeshBleConnection
import com.project.blue_command.data.ble.mesh.MeshRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Testy jednostkowe dla NordicMeshService.
 *
 * Weryfikują:
 * - startListening() łączy się z proxy jeśli MAC jest dostępny
 * - stopListening() rozłącza proxy
 * - broadcastPayload() przekazuje dane do MeshRepository
 * - dane przychodzące z proxy są wstrzykiwane do MeshRepository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NordicMeshServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockMeshRepository: MeshRepository
    private lateinit var mockProxyConnection: MeshBleConnection
    private lateinit var mockHardwareDetector: HardwareDetector
    private lateinit var nordicMeshService: NordicMeshService

    // Flows których NordicMeshService wymaga w init{}
    private val fakeIncomingMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    private val fakeOutgoingPdu = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockMeshRepository = mockk(relaxed = true)
        mockProxyConnection = mockk(relaxed = true)
        mockHardwareDetector = mockk(relaxed = true)

        // Kluczowe: zapewniamy działające flows, żeby init{} NordicMeshService nie crashował
        every { mockMeshRepository.incomingMessages } returns fakeIncomingMessages
        every { mockMeshRepository.outgoingPdu } returns fakeOutgoingPdu

        nordicMeshService = NordicMeshService(
            mockMeshRepository,
            mockProxyConnection,
            mockHardwareDetector
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `startListening should connect to proxy if MAC is available`() = runTest {
        // Arrange
        val testMac = "AA:BB:CC:DD:EE:FF"
        val deviceStateFlow = MutableStateFlow<MeshDeviceState>(MeshDeviceState.Proxy(testMac))
        every { mockHardwareDetector.deviceState } returns deviceStateFlow

        // Act
        nordicMeshService.startListening()

        // Assert
        coVerify(exactly = 1) { mockProxyConnection.connectToProxy(testMac) }
    }

    @Test
    fun `startListening should NOT connect if state is None`() = runTest {
        // Arrange
        val deviceStateFlow = MutableStateFlow<MeshDeviceState>(MeshDeviceState.None)
        every { mockHardwareDetector.deviceState } returns deviceStateFlow

        // Act
        nordicMeshService.startListening()

        // Assert – brak połączenia gdy nie ma sprzętu
        coVerify(exactly = 0) { mockProxyConnection.connectToProxy(any()) }
    }

    @Test
    fun `stopListening should disconnect proxy`() = runTest {
        // Arrange
        val deviceStateFlow = MutableStateFlow<MeshDeviceState>(MeshDeviceState.Proxy("AA:BB:CC:DD:EE:FF"))
        every { mockHardwareDetector.deviceState } returns deviceStateFlow
        nordicMeshService.startListening()

        // Act
        nordicMeshService.stopListening()

        // Assert
        coVerify(exactly = 1) { mockProxyConnection.disconnectProxy() }
    }

    @Test
    fun `broadcastPayload should call sendGenericOnOffMessage on MeshRepository`() = runTest {
        // Arrange
        val deviceStateFlow = MutableStateFlow<MeshDeviceState>(MeshDeviceState.Proxy("AA:BB:CC:DD:EE:FF"))
        every { mockHardwareDetector.deviceState } returns deviceStateFlow
        nordicMeshService.startListening()

        val testPayload = byteArrayOf(0x01, 0x02, 0x03) // bajt[2] = 3 -> nieparzyste -> isOn=true

        // Act
        nordicMeshService.broadcastPayload(testPayload)

        // Assert
        coVerify(exactly = 1) { mockMeshRepository.sendGenericOnOffMessage(true) }
    }

    @Test
    fun `incoming proxy data should be passed to MeshRepository handleNotifications`() = runTest {
        // Arrange – przechwytujemy listener ustawiony przez NordicMeshService
        val callbackSlot = slot<(ByteArray) -> Unit>()
        every { mockProxyConnection.setOnDataReceivedListener(capture(callbackSlot)) } answers {}

        // Tworzymy nowy obiekt po zainstalowaniu listenera
        nordicMeshService = NordicMeshService(
            mockMeshRepository,
            mockProxyConnection,
            mockHardwareDetector
        )

        val testData = byteArrayOf(0xFF.toByte(), 0xAA.toByte())

        // Act – symulujemy odebranie danych przez BLE
        callbackSlot.captured.invoke(testData)

        // Assert
        verify(exactly = 1) { mockMeshRepository.handleNotifications(testData) }
    }
}
