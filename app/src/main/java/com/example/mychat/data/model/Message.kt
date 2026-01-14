package com.example.mychat.data.model

data class Message(
    val id: String,
    val from: String,
    val to: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENT,
    DELIVERED
}
