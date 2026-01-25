package com.example.mychat.data.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val isOnline: Boolean = false,
    val role: String = "user", // "admin", "agency", "user"
    val approved: Boolean = true
)
