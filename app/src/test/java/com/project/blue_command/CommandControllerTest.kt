package com.project.blue_command

import android.app.Application
import android.content.Context
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.TacticalRadioManager
import com.project.blue_command.data.ble.BleBroadcastService
import com.project.blue_command.data.ble.BleServiceFactory
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

        mockkConstructor(TacticalRadioManager::class)
        incomingCommandsFlow = MutableSharedFlow()

        every { anyConstructed<TacticalRadioManager>().incomingCommands } returns incomingCommandsFlow
        every { anyConstructed<TacticalRadioManager>().startListening() } just runs
        every { anyConstructed<TacticalRadioManager>().stopListening() } just runs
        coEvery { anyConstructed<TacticalRadioManager>().sendCommand(any()) } just runs

        mockkObject(BleServiceFactory)
        every { BleServiceFactory.getClassicBleBroadcastService(any()) } returns mockk<BleBroadcastService>(relaxed = true)

        commandController = CommandController(mockApp, mockAuthController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `received command should respond 2 times ACK with the same msgId`() = runTest {
        // Nie sprawdzamy zapisu do bazy danych w tym unit test case
        coEvery { mockAppDao.insertCommand(any()) } just runs

        advanceUntilIdle()

        val incomingPacket = byteArrayOf(0x01, 100, 5, 123)

        // Symulacja otrzymania komunikatu przez BLE
        incomingCommandsFlow.emit(incomingPacket)

        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockAppDao.insertCommand(any())
        }

        // Po odebraniu wysyłane są 2 wiadomości ACK, zeby zmaksymalizować prawdopodobienstwo
        // otrzymania ACK przez druga strone
        coVerify(exactly = 2) {
            anyConstructed<TacticalRadioManager>().sendCommand(
                match { it[0].toInt() == 0x02 && it[1].toInt() == 100 }
            )
        }
    }

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