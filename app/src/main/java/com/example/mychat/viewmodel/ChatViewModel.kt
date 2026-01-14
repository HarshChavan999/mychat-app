package com.example.mychat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mychat.data.model.Message
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.ChatRepository
import com.example.mychat.data.websocket.WebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages: StateFlow<List<Message>> = _chatMessages

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers

    private val _currentChatUser = MutableStateFlow<User?>(null)
    val currentChatUser: StateFlow<User?> = _currentChatUser

    private val _connectionState = MutableStateFlow<WebSocketManager.ConnectionState>(WebSocketManager.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketManager.ConnectionState> = _connectionState

    // History loading states
    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory

    private val _hasMoreHistory = MutableStateFlow(false)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError

    // Track the oldest message timestamp for pagination
    private var oldestMessageTimestamp: Long? = null

    init {
        viewModelScope.launch {
            chatRepository.messages.collect { messages ->
                _messages.value = messages
            }
        }
        viewModelScope.launch {
            chatRepository.chatMessages.collect { messages ->
                _chatMessages.value = messages
            }
        }
        viewModelScope.launch {
            chatRepository.onlineUsers.collect { users ->
                _onlineUsers.value = users
            }
        }
        viewModelScope.launch {
            chatRepository.currentChatUser.collect { user ->
                _currentChatUser.value = user
            }
        }
        viewModelScope.launch {
            webSocketManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        // Listen to history responses
        viewModelScope.launch {
            webSocketManager.historyResponse.collect { historyResponse ->
                handleHistoryResponse(
                    historyResponse.withUserId,
                    historyResponse.messages,
                    historyResponse.hasMore
                )
            }
        }

        // Listen to errors
        viewModelScope.launch {
            webSocketManager.errorReceived.collect { error ->
                handleHistoryError(error)
            }
        }
    }

    fun connect() {
        chatRepository.connect()
    }

    fun disconnect() {
        chatRepository.disconnect()
    }

    suspend fun authenticate(token: String) {
        chatRepository.authenticate(token)
    }

    fun sendMessage(toUserId: String, content: String) {
        if (content.isNotBlank()) {
            chatRepository.sendMessage(toUserId, content)
        }
    }

    fun setCurrentChatUser(user: User) {
        chatRepository.setCurrentChatUser(user)
    }

    fun clearCurrentChatUser() {
        chatRepository.clearCurrentChatUser()
    }

    fun updateOnlineUsers(users: List<User>) {
        chatRepository.updateOnlineUsers(users)
    }

    fun getMessagesWithUser(userId: String): List<Message> {
        return chatRepository.getMessagesWithUser(userId)
    }

    fun clearMessages() {
        chatRepository.clearMessages()
    }

    // History loading functionality
    fun loadChatHistory() {
        val currentUser = _currentChatUser.value ?: return

        if (_isLoadingHistory.value) return // Prevent multiple concurrent requests

        _isLoadingHistory.value = true
        _historyError.value = null

        // Use oldestMessageTimestamp for pagination, null for initial load
        webSocketManager.sendHistoryRequest(
            withUserId = currentUser.id,
            limit = 20, // Load 20 messages at a time
            beforeTimestamp = oldestMessageTimestamp
        )
    }

    fun loadMoreHistory() {
        if (!_hasMoreHistory.value || _isLoadingHistory.value) return
        loadChatHistory()
    }

    private fun handleHistoryResponse(withUserId: String, historyMessages: List<Message>, hasMore: Boolean) {
        _isLoadingHistory.value = false
        _hasMoreHistory.value = hasMore

        if (historyMessages.isNotEmpty()) {
            // Update oldest message timestamp for pagination
            val oldestInResponse = historyMessages.minByOrNull { it.timestamp }?.timestamp
            if (oldestMessageTimestamp == null || (oldestInResponse != null && oldestInResponse < oldestMessageTimestamp!!)) {
                oldestMessageTimestamp = oldestInResponse
            }

            // Add history messages to repository (they will be merged with real-time messages)
            chatRepository.addHistoryMessages(historyMessages)
        }
    }

    private fun handleHistoryError(error: String) {
        _isLoadingHistory.value = false
        _historyError.value = error
    }

    fun clearHistoryError() {
        _historyError.value = null
    }

    // Called when starting a new chat conversation
    fun initializeChatHistory() {
        // Reset history state when starting new chat
        oldestMessageTimestamp = null
        _hasMoreHistory.value = false
        _historyError.value = null

        // Load initial history
        loadChatHistory()
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.cleanup()
    }
}
