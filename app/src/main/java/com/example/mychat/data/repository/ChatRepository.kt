package com.example.mychat.data.repository

import com.example.mychat.data.model.Message
import com.example.mychat.data.model.MessageStatus
import com.example.mychat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

class ChatRepository(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: Flow<List<Message>> = _messages.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: Flow<List<User>> = _onlineUsers.asStateFlow()

    private val _currentChatUser = MutableStateFlow<User?>(null)
    val currentChatUser: Flow<User?> = _currentChatUser.asStateFlow()

    // Combined flow for messages with specific user
    val chatMessages: Flow<List<Message>> = combine(messages, currentChatUser) { messages, currentUser ->
        android.util.Log.d("ChatRepository", "Combining messages flow: ${messages.size} total messages, current user: ${currentUser?.id}")
        if (currentUser != null) {
            val filteredMessages = messages.filter { it.from == currentUser.id || it.to == currentUser.id }
            android.util.Log.d("ChatRepository", "Filtered messages for user ${currentUser.id}: ${filteredMessages.size}")
            filteredMessages
        } else {
            android.util.Log.d("ChatRepository", "No current user set, returning empty list")
            emptyList()
        }
    }

    private var chatMessagesListener: ListenerRegistration? = null

    init {
        // Listen to authentication state changes
        firebaseAuth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                setupMessageListener()
            } else {
                cleanupListeners()
            }
        }
    }

    private fun setupMessageListener() {
        val currentUser = firebaseAuth.currentUser ?: return
        val userId = currentUser.uid

        // Listen to messages for the current chat user only
        _currentChatUser.value?.let { currentChatUser ->
            val otherUserId = currentChatUser.id

            // Listen to messages between current user and the selected chat user
            val chatMessagesListener = firestore.collection("messages")
                .whereIn("sender", listOf(userId, otherUserId))
                .whereIn("receiverId", listOf(userId, otherUserId))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("ChatRepository", "Error listening to chat messages: ${error.message}")
                        return@addSnapshotListener
                    }

                    snapshot?.let { querySnapshot ->
                        val messages = mutableListOf<Message>()
                        for (document in querySnapshot.documents) {
                            val message = documentToMessage(document)
                            if (message != null) {
                                messages.add(message)
                            }
                        }

                        // Update messages list, avoiding duplicates
                        val currentMessages = _messages.value.toMutableList()
                        val existingIds = currentMessages.map { it.id }.toSet()
                        val newMessages = messages.filter { it.id !in existingIds }

                        currentMessages.addAll(newMessages)
                        _messages.value = currentMessages.sortedBy { it.timestamp }
                    }
                }

            // Store the listener
            this.chatMessagesListener = chatMessagesListener
        }
    }

    private fun cleanupListeners() {
        chatMessagesListener?.remove()
        chatMessagesListener = null
        _messages.value = emptyList()
    }

    private fun documentToMessage(document: com.google.firebase.firestore.DocumentSnapshot): Message? {
        return try {
            val data = document.data ?: return null
            val message = Message(
                id = document.id,
                from = data["sender"] as? String ?: "",
                to = data["receiverId"] as? String ?: "",
                content = data["text"] as? String ?: "",
                timestamp = (data["timestamp"] as? Long) ?: 0L,
                status = MessageStatus.SENT // messages collection doesn't have status field
            )
            
            // Debug logging
            android.util.Log.d("ChatRepository", "Loaded message: ${message.id} from ${message.from} to ${message.to} at ${message.timestamp}")
            
            message
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error parsing message document: ${e.message}")
            null
        }
    }

    fun connect() {
        // Firestore is always connected, no explicit connect needed
    }

    fun disconnect() {
        cleanupListeners()
    }

    suspend fun authenticate(token: String) {
        // Firebase Auth handles authentication separately
        // This method can be used if you need custom auth flow
    }

    fun sendMessage(toUserId: String, content: String) {
        val currentUser = firebaseAuth.currentUser ?: return
        val fromUserId = currentUser.uid

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messageId = generateMessageId()
                val timestamp = System.currentTimeMillis()

                // Create message data for Firestore (messages collection format)
                val messageData = hashMapOf(
                    "sender" to fromUserId,
                    "receiverId" to toUserId,
                    "text" to content,
                    "timestamp" to timestamp,
                    "chatId" to "${fromUserId}_${toUserId}"
                )

                // Save to Firestore
                firestore.collection("messages")
                    .document(messageId)
                    .set(messageData)
                    .await()

                // Update local state
                val message = Message(
                    id = messageId,
                    from = fromUserId,
                    to = toUserId,
                    content = content,
                    timestamp = timestamp,
                    status = MessageStatus.SENT
                )

                val currentMessages = _messages.value.toMutableList()
                currentMessages.add(message)
                _messages.value = currentMessages.sortedBy { it.timestamp }

            } catch (e: Exception) {
                // Handle error - could queue for retry
                // For now, just log
                e.printStackTrace()
            }
        }
    }

    fun setCurrentChatUser(user: User) {
        android.util.Log.d("ChatRepository", "Setting current chat user: ${user.id} (${user.displayName})")
        _currentChatUser.value = user
        
        // Clean up existing listener and set up new one for this user
        cleanupListeners()
        setupMessageListener()
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
        
        // Also update in Firestore
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("chat_messages")
                    .document(messageId)
                    .update("status", "delivered")
                    .await()
                Log.d("ChatRepository", "Message $messageId marked as delivered in Firestore")
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to update message status in Firestore: ${e.message}")
            }
        }
    }

    fun getMessagesWithUser(userId: String): List<Message> {
        return _messages.value.filter { it.from == userId || it.to == userId }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    private fun generateMessageId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    // Load message history for current chat user
    suspend fun loadMessageHistory(otherUserId: String, limit: Long = 50) {
        try {
            val currentUserId = getCurrentUserId() ?: return

            val query = firestore.collection("messages")
                .whereIn("sender", listOf(currentUserId, otherUserId))
                .whereIn("receiverId", listOf(currentUserId, otherUserId))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val historyMessages = mutableListOf<Message>()
            for (document in query.documents) {
                val message = documentToMessage(document)
                if (message != null) {
                    historyMessages.add(message)
                }
            }

            // Add to existing messages, avoiding duplicates
            val currentMessages = _messages.value.toMutableList()
            val existingIds = currentMessages.map { it.id }.toSet()
            val newMessages = historyMessages.filter { it.id !in existingIds }

            currentMessages.addAll(newMessages)
            _messages.value = currentMessages.sortedBy { it.timestamp }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
