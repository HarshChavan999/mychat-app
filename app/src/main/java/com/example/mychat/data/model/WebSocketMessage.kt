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
    HISTORY_RESPONSE
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
    val content: String,
    val timestamp: Long
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
