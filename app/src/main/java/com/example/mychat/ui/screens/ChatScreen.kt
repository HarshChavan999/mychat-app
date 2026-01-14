package com.example.mychat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mychat.data.model.Message
import com.example.mychat.data.model.User
import com.example.mychat.data.websocket.WebSocketManager
import com.example.mychat.ui.components.MessageBubble
import com.example.mychat.ui.components.MessageInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUser: User?,
    chatUser: User?,
    messages: List<Message>,
    connectionState: WebSocketManager.ConnectionState,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    onRetryConnection: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatUser?.displayName ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // You can add a back icon here
                        Text("←")
                    }
                },
                actions = {
                    // Connection status indicator
                    val statusColor = when (connectionState) {
                        WebSocketManager.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        WebSocketManager.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                        WebSocketManager.ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        color = statusColor,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(12.dp)
                    ) {}
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    val isFromCurrentUser = message.from == currentUser?.id
                    MessageBubble(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Connection error banner
            if (connectionState == WebSocketManager.ConnectionState.ERROR) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connection lost. Messages may not be delivered.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = { onRetryConnection?.invoke() }
                        ) {
                            Text(
                                text = "Retry",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Message input
            MessageInput(
                onSendMessage = { content ->
                    onSendMessage(content)
                    coroutineScope.launch {
                        // Scroll to bottom after sending
                        listState.animateScrollToItem(messages.size)
                    }
                },
                enabled = connectionState == WebSocketManager.ConnectionState.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
