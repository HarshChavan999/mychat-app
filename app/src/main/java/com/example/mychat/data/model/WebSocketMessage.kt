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
    ERROR
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
