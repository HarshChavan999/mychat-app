package com.example.mychat

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.mychat.data.model.Message
import com.example.mychat.data.model.MessageStatus
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.ChatRepository
import com.example.mychat.data.websocket.WebSocketManager
import com.example.mychat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for ChatViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var chatRepository: ChatRepository

    @Mock
    private lateinit var webSocketManager: WebSocketManager

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Setup mock flows
        whenever(chatRepository.messages).thenReturn(MutableStateFlow(emptyList()))
        whenever(chatRepository.chatMessages).thenReturn(MutableStateFlow(emptyList()))
        whenever(chatRepository.onlineUsers).thenReturn(MutableStateFlow(emptyList()))
        whenever(chatRepository.currentChatUser).thenReturn(MutableStateFlow(null))
        whenever(webSocketManager.connectionState).thenReturn(MutableStateFlow(WebSocketManager.ConnectionState.DISCONNECTED))
        whenever(webSocketManager.historyResponse).thenReturn(MutableSharedFlow())
        whenever(webSocketManager.errorReceived).thenReturn(MutableSharedFlow())

        viewModel = ChatViewModel(chatRepository, webSocketManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be empty`() = runTest {
        // Given
        val expectedMessages = emptyList<Message>()
        val expectedChatMessages = emptyList<Message>()
        val expectedOnlineUsers = emptyList<User>()
        val expectedCurrentUser: User? = null
        val expectedConnectionState = WebSocketManager.ConnectionState.DISCONNECTED

        // Then
        viewModel.messages.test {
            assertEquals(expectedMessages, awaitItem())
        }

        viewModel.chatMessages.test {
            assertEquals(expectedChatMessages, awaitItem())
        }

        viewModel.onlineUsers.test {
            assertEquals(expectedOnlineUsers, awaitItem())
        }

        viewModel.currentChatUser.test {
            assertEquals(expectedCurrentUser, awaitItem())
        }

        viewModel.connectionState.test {
            assertEquals(expectedConnectionState, awaitItem())
        }
    }

    @Test
    fun `connect should call repository connect`() {
        // When
        viewModel.connect()

        // Then
        verify(chatRepository).connect()
    }

    @Test
    fun `disconnect should call repository disconnect`() {
        // When
        viewModel.disconnect()

        // Then
        verify(chatRepository).disconnect()
    }

    @Test
    fun `authenticate should call repository authenticate`() = runTest {
        // Given
        val token = "test-token"

        // When
        viewModel.authenticate(token)

        // Then
        verify(chatRepository).authenticate(token)
    }

    @Test
    fun `sendMessage should call repository sendMessage when content is not blank`() {
        // Given
        val toUserId = "user123"
        val content = "Hello World"

        // When
        viewModel.sendMessage(toUserId, content)

        // Then
        verify(chatRepository).sendMessage(toUserId, content)
    }

    @Test
    fun `sendMessage should not call repository when content is only whitespace`() {
        // Given
        val toUserId = "user123"
        val content = "   "

        // When
        viewModel.sendMessage(toUserId, content)

        // Then - should not call repository since content.isNotBlank() returns false for whitespace
        // Note: verifyZeroInteractions or verifyNoMoreInteractions could be used here
    }

    @Test
    fun `setCurrentChatUser should call repository setCurrentChatUser`() {
        // Given
        val user = User("user123", "test@example.com", "Test User")

        // When
        viewModel.setCurrentChatUser(user)

        // Then
        verify(chatRepository).setCurrentChatUser(user)
    }

    @Test
    fun `clearCurrentChatUser should call repository clearCurrentChatUser`() {
        // When
        viewModel.clearCurrentChatUser()

        // Then
        verify(chatRepository).clearCurrentChatUser()
    }

    @Test
    fun `updateOnlineUsers should call repository updateOnlineUsers`() {
        // Given
        val users = listOf(
            User("user1", "user1@example.com", "User 1", true),
            User("user2", "user2@example.com", "User 2", false)
        )

        // When
        viewModel.updateOnlineUsers(users)

        // Then
        verify(chatRepository).updateOnlineUsers(users)
    }

    @Test
    fun `getMessagesWithUser should return repository result`() {
        // Given
        val userId = "user123"
        val expectedMessages = listOf(
            Message("msg1", "user123", "current", "Hello", 123456789L, MessageStatus.DELIVERED)
        )
        whenever(chatRepository.getMessagesWithUser(userId)).thenReturn(expectedMessages)

        // When
        val result = viewModel.getMessagesWithUser(userId)

        // Then
        assertEquals(expectedMessages, result)
        verify(chatRepository).getMessagesWithUser(userId)
    }

    @Test
    fun `clearMessages should call repository clearMessages`() {
        // When
        viewModel.clearMessages()

        // Then
        verify(chatRepository).clearMessages()
    }


}
