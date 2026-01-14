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

    override fun onCleared() {
        super.onCleared()
        webSocketManager.cleanup()
    }
}
