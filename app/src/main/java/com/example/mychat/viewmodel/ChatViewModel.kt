package com.example.mychat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mychat.data.model.Message
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages: StateFlow<List<Message>> = _chatMessages

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers

    private val _currentChatUser = MutableStateFlow<User?>(null)
    val currentChatUser: StateFlow<User?> = _currentChatUser

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

        viewModelScope.launch {
            try {
                chatRepository.loadMessageHistory(currentUser.id)
                _isLoadingHistory.value = false
                // For now, assume we have more history available
                _hasMoreHistory.value = true
            } catch (e: Exception) {
                _isLoadingHistory.value = false
                _historyError.value = e.message ?: "Failed to load chat history"
            }
        }
    }

    fun loadMoreHistory() {
        if (!_hasMoreHistory.value || _isLoadingHistory.value) return
        loadChatHistory()
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
        // No cleanup needed for Firestore
    }
}
