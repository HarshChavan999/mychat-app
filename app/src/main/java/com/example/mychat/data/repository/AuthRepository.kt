package com.example.mychat.data.repository

import com.example.mychat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: Flow<User?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        // Listen to auth state changes
        firebaseAuth.addAuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            _currentUser.value = firebaseUser?.toUser()
        }
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            _authState.value = AuthState.Loading
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            _authState.value = AuthState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            _authState.value = AuthState.Loading
            firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            _authState.value = AuthState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Registration failed")
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(): Result<Unit> {
        return try {
            _authState.value = AuthState.Loading
            // Skip Firebase auth - create a dummy user directly
            val dummyUser = User(
                id = "guest-${System.currentTimeMillis()}",
                email = "",
                displayName = "Guest User",
                isOnline = true
            )
            _currentUser.value = dummyUser
            _authState.value = AuthState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Anonymous sign-in failed")
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        _authState.value = AuthState.Idle
    }

    fun getIdToken(): String? {
        // For guest users, return a dummy token
        return _currentUser.value?.let { "guest-token-${it.id}" } ?: firebaseAuth.currentUser?.getIdToken(false)?.result?.token
    }

    suspend fun getIdTokenAsync(): String? {
        // For guest users, return a dummy token
        return _currentUser.value?.let { "guest-token-${it.id}" } ?: try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }
    }

    private fun FirebaseUser.toUser(): User {
        return User(
            id = uid,
            email = email ?: "",
            displayName = displayName ?: email ?: "Anonymous",
            isOnline = true
        )
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
