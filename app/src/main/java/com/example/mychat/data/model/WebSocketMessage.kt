package com.example.mychat.data.model

data class WebSocketMessage(
    val type: MessageType,
    val payload: Any? = null,
    val id: String? = null,
    val timestamp: Long? = null
)

enum class MessageType {
    AUTH,
    MESSAGE,
    ACK,
    PING,
    PONG,
    ERROR,
    HISTORY_REQUEST,
    HISTORY_RESPONSE,
    SYNC_REQUEST,
    SYNC_RESPONSE
}

// Payload classes
data class AuthPayload(
    val token: String
)

data class MessagePayload(
    val to: String,
    val content: String
)

data class AckPayload(
    val messageId: String
)

data class AuthResponse(
    val success: Boolean,
    val user: User? = null,
    val error: String? = null
)

data class MessageResponse(
    val id: String,
    val from: String,
    val to: String,
    val content: String,
    val timestamp: Long,
    val status: String? = null
)

data class HistoryRequestPayload(
    val withUserId: String,
    val limit: Int? = null,
    val beforeTimestamp: Long? = null
)

data class HistoryResponsePayload(
    val withUserId: String,
    val messages: List<Message>,
    val hasMore: Boolean
)

data class SyncRequestPayload(
    val lastSyncTimestamp: Long,
    val limit: Int? = null
)

data class SyncResponsePayload(
    val messages: List<MessageResponse>,
    val syncTimestamp: Long,
    val hasMore: Boolean? = false
)
