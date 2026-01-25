package com.example.mychat.data.repository

import android.content.Context
import com.example.mychat.data.model.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
            if (firebaseUser != null) {
                // Fetch user role data synchronously for immediate access
                kotlinx.coroutines.runBlocking {
                    try {
                        val updatedUser = firebaseUser.toUser()
                        _currentUser.value = updatedUser
                        android.util.Log.d("AuthRepository", "User authenticated: ${updatedUser.displayName}, role: ${updatedUser.role}, approved: ${updatedUser.approved}")
                    } catch (e: Exception) {
                        // Fallback to basic user if Firestore fetch fails
                        android.util.Log.w("AuthRepository", "Failed to fetch user role data: ${e.message}, using fallback")
                        _currentUser.value = User(
                            id = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            displayName = firebaseUser.displayName ?: firebaseUser.email ?: "Anonymous",
                            isOnline = true,
                            role = "user", // Default
                            approved = true // Default for testing
                        )
                    }
                }
            } else {
                _currentUser.value = null
                android.util.Log.d("AuthRepository", "User signed out")
            }
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
            firebaseAuth.signInAnonymously().await()
            _authState.value = AuthState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Anonymous sign-in failed")
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> {
        return try {
            android.util.Log.d("AuthRepository", "Starting Google sign-in with account: ${account.email}")
            _authState.value = AuthState.Loading

            // Get the ID token
            val idToken = account.idToken
                ?: throw Exception("Failed to get ID token from Google account")

            android.util.Log.d("AuthRepository", "Got ID token, creating Firebase credential")

            // Create Firebase credential
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            // Sign in to Firebase
            android.util.Log.d("AuthRepository", "Calling Firebase signInWithCredential...")
            val authResult = firebaseAuth.signInWithCredential(credential).await()

            android.util.Log.d("AuthRepository", "Firebase auth result received. User: ${authResult.user?.email}")

            // Ensure we have a valid user before marking as success
            if (authResult.user != null) {
                android.util.Log.d("AuthRepository", "Firebase auth successful, setting state to Success")
                _authState.value = AuthState.Success
                Result.success(Unit)
            } else {
                throw Exception("Firebase authentication succeeded but user is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google sign-in failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Google sign-in failed")
            Result.failure(e)
        }
    }

    fun getGoogleSignInClient(context: Context): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        // This method can be used to get the GoogleSignInClient for starting the sign-in flow
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("387994411670-q5354384h89rasqgh4qtdiffnfc7hcps.apps.googleusercontent.com") // Android client ID
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, gso)
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

    private suspend fun FirebaseUser.toUser(): User {
        // Fetch user data from Firestore to get role and approval status
        return try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val data = userDoc.data
                User(
                    id = uid,
                    email = email ?: "",
                    displayName = displayName ?: email ?: "Anonymous",
                    isOnline = true,
                    role = data?.get("role") as? String ?: "user",
                    approved = data?.get("approved") as? Boolean ?: false
                )
            } else {
                // User document doesn't exist, create default user
                User(
                    id = uid,
                    email = email ?: "",
                    displayName = displayName ?: email ?: "Anonymous",
                    isOnline = true,
                    role = "user",
                    approved = true
                )
            }
        } catch (e: Exception) {
            // Fallback to basic user if Firestore fetch fails
            User(
                id = uid,
                email = email ?: "",
                displayName = displayName ?: email ?: "Anonymous",
                isOnline = true,
                role = "user",
                approved = true
            )
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
