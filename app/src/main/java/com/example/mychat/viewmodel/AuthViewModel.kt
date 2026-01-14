package com.example.mychat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.AuthRepository
import com.example.mychat.data.repository.AuthState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // Flow to notify when user successfully authenticates (for WebSocket connection)
    private val _authSuccess = MutableSharedFlow<User>()
    val authSuccess: SharedFlow<User> = _authSuccess

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
            }
        }
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _authState.value = state
                // Emit auth success when authentication completes successfully
                if (state is AuthState.Success && _currentUser.value != null) {
                    _authSuccess.emit(_currentUser.value!!)
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            authRepository.signInWithEmailAndPassword(email, password)
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            authRepository.createUserWithEmailAndPassword(email, password)
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            authRepository.signInAnonymously()
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun getIdToken(): String? {
        return authRepository.getIdToken()
    }

    suspend fun getIdTokenAsync(): String? {
        return authRepository.getIdTokenAsync()
    }
}
