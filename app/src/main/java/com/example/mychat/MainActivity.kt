package com.example.mychat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.AuthRepository
import com.example.mychat.data.repository.ChatRepository
import com.example.mychat.data.websocket.NetworkConnectivityManager
import com.example.mychat.data.websocket.WebSocketManager
import com.example.mychat.ui.screens.ChatScreen
import com.example.mychat.ui.screens.LoginScreen
import com.example.mychat.ui.theme.MychatTheme
import com.example.mychat.viewmodel.AuthViewModel
import com.example.mychat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MychatTheme {
                ChatApp()
            }
        }
    }
}

@Composable
fun ChatNavigation(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    currentUser: User
) {
    val scope = rememberCoroutineScope()
    var showChat by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    // Connect to WebSocket when authentication succeeds
    LaunchedEffect(Unit) {
        authViewModel.authSuccess.collect { user ->
            val token = authViewModel.getIdToken()
            if (token != null) {
                chatViewModel.connect()
                chatViewModel.authenticate(token)
            }
        }
    }

    // For demo purposes, create a mock user to chat with
    val demoUser = User(
        id = "demo-user-123", // Use a consistent ID
        email = "demo@example.com",
        displayName = "Demo User",
        isOnline = true
    )

    val chatMessages by chatViewModel.chatMessages.collectAsState()
    val connectionState by chatViewModel.connectionState.collectAsState()

    if (showChat && selectedUser != null) {
        val chatUser = selectedUser
        ChatScreen(
            currentUser = currentUser,
            chatUser = chatUser,
            messages = chatMessages,
            connectionState = connectionState,
            onSendMessage = { content ->
                chatViewModel.sendMessage(chatUser!!.id, content)
            },
            onBack = { showChat = false }
        )
    } else {
        // Simple user selection screen for demo
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome, ${currentUser.displayName}!",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Select a user to chat with:",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedButton(
                onClick = {
                    selectedUser = demoUser
                    chatViewModel.setCurrentChatUser(demoUser)
                    showChat = true
                }
            ) {
                Text("Chat with ${demoUser.displayName}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    authViewModel.signOut()
                }
            ) {
                Text("Sign Out")
            }
        }
    }
}

@Composable
fun ChatApp() {
    // Get application context for network monitoring
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initialize dependencies
    val authRepository = AuthRepository()
    val networkConnectivityManager = NetworkConnectivityManager(context)
    val webSocketManager = WebSocketManager(networkConnectivityManager = networkConnectivityManager)
    val chatRepository = ChatRepository(webSocketManager, authRepository)

    // Initialize ViewModels
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    val chatViewModel: ChatViewModel = viewModel { ChatViewModel(chatRepository, webSocketManager) }

    // Main app navigation based on auth state
    val currentUser by authViewModel.currentUser.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    when {
        currentUser != null -> {
            // User is authenticated, show chat interface
            ChatNavigation(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                currentUser = currentUser!!
            )
        }
        else -> {
            // User not authenticated, show login screen
            LoginScreen(
                authState = authState,
                onSignIn = { email, password ->
                    authViewModel.signIn(email, password)
                },
                onSignUp = { email, password ->
                    authViewModel.signUp(email, password)
                },
                onAnonymousSignIn = {
                    authViewModel.signInAnonymously()
                }
            )
        }
    }
}
