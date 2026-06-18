package com.project.blue_command

import android.app.Application
import android.content.Context
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.TacticalRadioManager
import com.project.blue_command.data.ble.BleBroadcastService
import com.project.blue_command.data.ble.BleServiceFactory
import com.project.blue_command.data.ble.NordicMeshService
import com.project.blue_command.data.database.AppDao
import com.project.blue_command.data.database.LocalAppDatabase
import com.project.blue_command.logic.AuthController
import com.project.blue_command.logic.CommandController
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.TacticalCommand
import com.project.blue_command.model.UserAccount
import com.project.blue_command.model.UserRole
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandControllerTest {

    private lateinit var incomingCommandsFlow: MutableSharedFlow<ByteArray>
    private lateinit var activeGroupFlow: MutableStateFlow<CombatGroup?>
    private lateinit var currentUserFlow: MutableStateFlow<UserAccount?>

    private val mockApp: Application = mockk(relaxed = true)
    private val mockAuthController: AuthController = mockk(relaxed = true)
    private val mockAppDao: AppDao = mockk(relaxed = true)

    private var mockUser: UserAccount = mockk(relaxed = true)
    private var mockGroup: CombatGroup = mockk(relaxed = true)


    private val testDispatcher = StandardTestDispatcher()

    private lateinit var commandController: CommandController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(LocalAppDatabase)
        val mockDatabase: LocalAppDatabase = mockk(relaxed = true)
        val mockContext: Context = mockk(relaxed = true)
        every { LocalAppDatabase.getDatabase(any<Context>()) } returns mockDatabase
        every { mockDatabase.appDao() } returns mockAppDao
        every { mockContext.applicationContext } returns mockContext

        mockkObject(SessionRepository)
        activeGroupFlow = MutableStateFlow(null)
        currentUserFlow = MutableStateFlow(null)
        every { SessionRepository.activeGroup } returns activeGroupFlow
        every { SessionRepository.currentUser } returns currentUserFlow

        val curUser = UserAccount(id = "user1", username = "Jan", role = UserRole.SOLDIER, password = "123")
        val curGroup = CombatGroup(
            id = "group1", name = "Alfa", memberIds = mutableListOf("user1","user2","user3"), groupKeyBase64 = "123"
        )

        activeGroupFlow.value = curGroup
        currentUserFlow.value = curUser

        mockUser = curUser
        mockGroup = curGroup

        every { mockAuthController.getUserById("user1") } returns curUser
        every { mockAuthController.getUserById("user2") } returns UserAccount("user2", "user2", "123", UserRole.SOLDIER)
        every { mockAuthController.getUserById("user3") } returns UserAccount("user3", "user3", "123", UserRole.SOLDIER)

        mockkConstructor(com.project.blue_command.data.ble.mesh.MeshRepository::class)
        mockkConstructor(com.project.blue_command.data.ble.mesh.BleMeshProxyManager::class)
        mockkConstructor(com.project.blue_command.data.ble.HardwareDetector::class)
        mockkConstructor(TacticalRadioManager::class)
        incomingCommandsFlow = MutableSharedFlow()

        every { anyConstructed<TacticalRadioManager>().incomingCommands } returns incomingCommandsFlow
        every { anyConstructed<TacticalRadioManager>().startListening() } just runs
        every { anyConstructed<TacticalRadioManager>().stopListening() } just runs
        coEvery { anyConstructed<TacticalRadioManager>().sendCommand(any()) } just runs

        mockkObject(BleServiceFactory)
        every { BleServiceFactory.getClassicBleBroadcastService(any()) } returns mockk<BleBroadcastService>(relaxed = true)
        every { BleServiceFactory.getNordicMeshService(any(), any()) } returns mockk<NordicMeshService>(relaxed = true)
        
        val mockBluetoothManager = mockk<android.bluetooth.BluetoothManager>(relaxed = true)
        every { mockApp.getSystemService(android.content.Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager

        commandController = CommandController(mockApp, mockAuthController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    /**
     * UWAGA: Ten test wymaga pełnego środowiska Android (UUID.randomUUID() przez SecureRandom
     * crashuje na czystym JVM w niektórych konfiguracjach JDK17+).
     * Przenies do testów instrumentalnych (androidTest) lub użyj Robolectric.
     */
    @Ignore("Wymaga pełnego środowiska Android - uruchom jako androidTest z Robolectric")
    @Test
    fun `received command should respond 2 times ACK with the same msgId`() = runTest {
        val entitySlot = slot<com.project.blue_command.data.database.CommandMessageEntity>()
        coEvery { mockAppDao.insertCommand(capture(entitySlot)) } just runs

        advanceUntilIdle()

        // Używamy prawdziwego kodu komendy oraz hasha istniejącego użytkownika ("user2")
        val senderId = "user2"
        val senderHash = (senderId.hashCode() and 0xFF).toByte()
        val cmdCode = TacticalCommand.ENEMY.code.toByte()
        val incomingPacket = byteArrayOf(0x01, 100, cmdCode, senderHash)

        // Symulacja otrzymania komunikatu przez BLE
        incomingCommandsFlow.emit(incomingPacket)

        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockAppDao.insertCommand(any())
        }

        // Sprawdzamy, czy CommandController poprawnie przetworzył pakiet
        val savedCommand = entitySlot.captured
        assert(savedCommand.senderId == senderId) { "Oczekiwano senderId=$senderId, otrzymano ${savedCommand.senderId}" }
        assert(savedCommand.commandLabel == TacticalCommand.ENEMY.label) { "Oczekiwano label=${TacticalCommand.ENEMY.label}, otrzymano ${savedCommand.commandLabel}" }

        // Po odebraniu wysyłane są 2 wiadomości ACK, zeby zmaksymalizować prawdopodobienstwo
        // otrzymania ACK przez druga strone
        val expectedAckHash = (mockUser.id.hashCode() and 0xFF).toByte()
        coVerify(exactly = 2) {
            anyConstructed<TacticalRadioManager>().sendCommand(
                match { it[0].toInt() == 0x02 && it[1].toInt() == 100 && it[2] == expectedAckHash }
            )
        }
    }

    /**
     * UWAGA: Ten test wymaga pełnego środowiska Android (UUID.randomUUID() przez SecureRandom
     * crashuje na czystym JVM w niektórych konfiguracjach JDK17+).
     * Przenies do testów instrumentalnych (androidTest) lub użyj Robolectric.
     */
    @Ignore("Wymaga pełnego środowiska Android - uruchom jako androidTest z Robolectric")
    @Test
    fun `sending command 8 times without all ack and after stop sending with failed state`() = runTest {
        coEvery { mockAppDao.insertCommand(any()) } just runs
        advanceUntilIdle()

        val payloadCommand = byteArrayOf(
            0x01,
            100,
            TacticalCommand.ENEMY.code.toByte(),
            (mockUser.id.hashCode() and 0xFF).toByte()
        )
        val payloadAck = byteArrayOf(
            0x02,
            100,
            TacticalCommand.ENEMY.code.toByte(),
            ("user2".hashCode() and 0xFF).toByte()
        )

        coEvery { anyConstructed<TacticalRadioManager>().sendCommand(payloadCommand) } just runs

        advanceUntilIdle()

        commandController.sendCommand(TacticalCommand.ENEMY)

        incomingCommandsFlow.emit(payloadAck)

        advanceTimeBy(2900)

        coVerify(exactly = 1) {
            anyConstructed<TacticalRadioManager>().sendCommand(payloadCommand)
        }
        advanceTimeBy(200)
        coVerify(exactly = 2) {
            anyConstructed<TacticalRadioManager>().sendCommand(payloadCommand)
        }
        advanceTimeBy(20900)

        coVerify(exactly = 8) {
            anyConstructed<TacticalRadioManager>().sendCommand(any<ByteArray>())
        }

        coVerify(exactly = 1) { mockAppDao.insertCommand(any()) }
    }

}