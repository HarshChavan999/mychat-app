package com.example.mychat.data.repository

import com.example.mychat.data.model.Message
import com.example.mychat.data.model.MessageResponse
import com.example.mychat.data.model.MessageStatus
import com.example.mychat.data.model.User
import com.example.mychat.data.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatRepository(
    private val webSocketManager: WebSocketManager,
    private val authRepository: AuthRepository
) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: Flow<List<Message>> = _messages.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: Flow<List<User>> = _onlineUsers.asStateFlow()

    private val _currentChatUser = MutableStateFlow<User?>(null)
    val currentChatUser: Flow<User?> = _currentChatUser.asStateFlow()

    // Message queue for offline messages
    private val _messageQueue = MutableStateFlow<List<QueuedMessage>>(emptyList())
    val messageQueue: Flow<List<QueuedMessage>> = _messageQueue.asStateFlow()

    // Data class for queued messages
    data class QueuedMessage(
        val message: Message,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Combined flow for messages with specific user
    val chatMessages: Flow<List<Message>> = combine(messages, currentChatUser) { messages, currentUser ->
        if (currentUser != null) {
            messages.filter { it.from == currentUser.id || it.to == currentUser.id }
        } else {
            emptyList()
        }
    }

    init {
        // Listen to WebSocket messages
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            webSocketManager.messageReceived.collect { messageResponse ->
                handleIncomingMessage(messageResponse)
            }
        }

        // Listen to auth responses
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            webSocketManager.authResponse.collect { authResponse ->
                if (authResponse.success) {
                    // User authenticated successfully
                    // Could update online users here if server provides that info
                }
            }
        }

        // Listen to ACK responses to update message status
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            webSocketManager.ackReceived.collect { messageId ->
                markMessageAsDelivered(messageId)
                // Remove from queue if it was queued
                removeFromQueue(messageId)
            }
        }

        // Listen to connection state changes to process queued messages
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            webSocketManager.connectionState.collect { state ->
                if (state == WebSocketManager.ConnectionState.CONNECTED) {
                    processMessageQueue()
                }
            }
        }
    }

    private fun handleIncomingMessage(messageResponse: MessageResponse) {
        val message = Message(
            id = messageResponse.id,
            from = messageResponse.from,
            to = getCurrentUserId() ?: "", // This should be set properly
            content = messageResponse.content,
            timestamp = messageResponse.timestamp,
            status = MessageStatus.DELIVERED
        )

        // Add message to list
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages

        // Send ACK back to server
        webSocketManager.sendAck(messageResponse.id)
    }

    fun connect() {
        webSocketManager.connect()
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    suspend fun authenticate(token: String) {
        webSocketManager.sendAuth(token)
    }

    fun sendMessage(toUserId: String, content: String) {
        // Create local message first
        val message = Message(
            id = generateMessageId(),
            from = getCurrentUserId() ?: "",
            to = toUserId,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT
        )

        // Add to local messages
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages

        // Check if WebSocket is connected
        if (webSocketManager.connectionState.value == WebSocketManager.ConnectionState.CONNECTED) {
            // Send via WebSocket immediately
            webSocketManager.sendMessage(toUserId, content)
        } else {
            // Queue message for later sending
            queueMessage(message)
        }
    }

    fun setCurrentChatUser(user: User) {
        _currentChatUser.value = user
    }

    fun clearCurrentChatUser() {
        _currentChatUser.value = null
    }

    fun updateOnlineUsers(users: List<User>) {
        _onlineUsers.value = users
    }

    fun markMessageAsDelivered(messageId: String) {
        val currentMessages = _messages.value.toMutableList()
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val updatedMessage = currentMessages[index].copy(status = MessageStatus.DELIVERED)
            currentMessages[index] = updatedMessage
            _messages.value = currentMessages
        }
    }

    fun getMessagesWithUser(userId: String): List<Message> {
        return _messages.value.filter { it.from == userId || it.to == userId }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun getCurrentUserId(): String? {
        // For now, we'll need to handle this differently since currentUser is a Flow
        // This is a temporary solution - in a real app you'd want to pass the current user
        // or collect the flow properly
        return "current-user-id" // TODO: Fix this properly
    }

    private fun generateMessageId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    private fun queueMessage(message: Message) {
        val queuedMessage = QueuedMessage(message)
        val currentQueue = _messageQueue.value.toMutableList()
        currentQueue.add(queuedMessage)
        _messageQueue.value = currentQueue
    }

    private fun removeFromQueue(messageId: String) {
        val currentQueue = _messageQueue.value.toMutableList()
        currentQueue.removeAll { it.message.id == messageId }
        _messageQueue.value = currentQueue
    }

    private fun processMessageQueue() {
        val currentQueue = _messageQueue.value
        if (currentQueue.isNotEmpty()) {
            // Send all queued messages
            currentQueue.forEach { queuedMessage ->
                webSocketManager.sendMessage(queuedMessage.message.to, queuedMessage.message.content)
            }
            // Clear the queue after sending
            _messageQueue.value = emptyList()
        }
    }

    // History message handling
    fun addHistoryMessages(historyMessages: List<Message>) {
        val currentMessages = _messages.value.toMutableList()

        // Filter out any history messages that are already in the list (by ID)
        val existingMessageIds = currentMessages.map { it.id }.toSet()
        val newHistoryMessages = historyMessages.filter { it.id !in existingMessageIds }

        // Add history messages to the beginning (they are older messages)
        currentMessages.addAll(0, newHistoryMessages)

        // Sort messages by timestamp to ensure proper ordering
        currentMessages.sortBy { it.timestamp }

        _messages.value = currentMessages
    }
}
