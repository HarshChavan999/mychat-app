package com.example.mychat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.AuthRepository
import com.example.mychat.data.repository.AuthState
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
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
                // Emit auth success when we have a valid user (regardless of auth state)
                // This handles both Firebase auth state changes and anonymous sign-in
                if (user != null) {
                    _authSuccess.emit(user)
                }
            }
        }
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _authState.value = state
                // For error states, we don't emit success
                if (state is AuthState.Error) {
                    // Clear any pending success emission
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

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        return authRepository.getGoogleSignInClient(context)
    }

    fun signInWithGoogle(account: GoogleSignInAccount) {
        android.util.Log.d("AuthViewModel", "signInWithGoogle called with account: ${account.email}")
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(account)
            android.util.Log.d("AuthViewModel", "signInWithGoogle result: ${result.isSuccess}")
            if (result.isFailure) {
                android.util.Log.e("AuthViewModel", "signInWithGoogle failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
