package com.example.mychat.data.websocket

import android.util.Log
import com.example.mychat.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val serverUrl: String = "wss://chat-backend-215828472999.us-central1.run.app", // Production Cloud Run URL
    private val networkConnectivityManager: NetworkConnectivityManager? = null
) {
    private val TAG = "WebSocketManager"
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Message events
    private val _messageReceived = MutableSharedFlow<MessageResponse>()
    val messageReceived: SharedFlow<MessageResponse> = _messageReceived

    private val _authResponse = MutableSharedFlow<AuthResponse>()
    val authResponse: SharedFlow<AuthResponse> = _authResponse

    private val _ackReceived = MutableSharedFlow<String>()
    val ackReceived: SharedFlow<String> = _ackReceived

    private val _errorReceived = MutableSharedFlow<String>()
    val errorReceived: SharedFlow<String> = _errorReceived

    private val _historyResponse = MutableSharedFlow<HistoryResponsePayload>()
    val historyResponse: SharedFlow<HistoryResponsePayload> = _historyResponse

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Ping/Pong for connection health
    private var pingJob: Job? = null

    init {
        // Listen to network connectivity changes for auto-reconnection
        networkConnectivityManager?.let { manager ->
            scope.launch {
                manager.isNetworkAvailable.collect { isAvailable ->
                    if (isAvailable && _connectionState.value == ConnectionState.DISCONNECTED) {
                        Log.d(TAG, "Network became available, attempting to reconnect WebSocket")
                        connect()
                    } else if (!isAvailable && _connectionState.value == ConnectionState.CONNECTED) {
                        Log.d(TAG, "Network lost, disconnecting WebSocket")
                        disconnect()
                    }
                }
            }
        }
    }

    enum class ConnectionState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, WebSocketListenerImpl())
        Log.d(TAG, "WebSocket connection initiated to $serverUrl")
        Log.d(TAG, "Server URL: $serverUrl")
        Log.d(TAG, "Client timeout: ${client.readTimeoutMillis}ms")
    }

    fun disconnect() {
        pingJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "WebSocket disconnected")
    }

    fun sendAuth(token: String) {
        val message = WebSocketMessage(
            type = MessageType.AUTH,
            payload = AuthPayload(token),
            timestamp = System.currentTimeMillis()
        )
        sendMessage(message)
    }

    fun sendMessage(to: String, content: String) {
        val message = WebSocketMessage(
            type = MessageType.MESSAGE,
            payload = MessagePayload(to, content),
            id = generateId(),
            timestamp = System.currentTimeMillis()
        )
        sendMessage(message)
    }

    fun sendAck(messageId: String) {
        val message = WebSocketMessage(
            type = MessageType.ACK,
            payload = AckPayload(messageId),
            timestamp = System.currentTimeMillis()
        )
        sendMessage(message)
    }

    fun sendHistoryRequest(withUserId: String, limit: Int? = null, beforeTimestamp: Long? = null) {
        val message = WebSocketMessage(
            type = MessageType.HISTORY_REQUEST,
            payload = HistoryRequestPayload(withUserId, limit, beforeTimestamp),
            timestamp = System.currentTimeMillis()
        )
        sendMessage(message)
    }

    private fun sendMessage(message: WebSocketMessage) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send message: not connected")
            return
        }

        try {
            val json = gson.toJson(message)
            webSocket?.send(json)
            Log.d(TAG, "Sent: ${message.type}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
        }
    }

    private fun startPingPong() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(30000) // Ping every 30 seconds
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val pingMessage = WebSocketMessage(
                        type = MessageType.PING,
                        timestamp = System.currentTimeMillis()
                    )
                    sendMessage(pingMessage)
                }
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val message = gson.fromJson(text, WebSocketMessage::class.java)

            when (message.type) {
                MessageType.AUTH -> {
                    val response = gson.fromJson(gson.toJson(message.payload), AuthResponse::class.java)
                    scope.launch { _authResponse.emit(response) }
                }
                MessageType.MESSAGE -> {
                    val response = gson.fromJson(gson.toJson(message.payload), MessageResponse::class.java)
                    scope.launch { _messageReceived.emit(response) }
                }
                MessageType.ACK -> {
                    val ackPayload = gson.fromJson(gson.toJson(message.payload), AckPayload::class.java)
                    scope.launch { _ackReceived.emit(ackPayload.messageId) }
                    Log.d(TAG, "ACK received for message: ${ackPayload.messageId}")
                }
                MessageType.PONG -> {
                    // Handle pong - connection is alive
                    Log.d(TAG, "PONG received")
                }
                MessageType.ERROR -> {
                    val error = message.payload.toString()
                    scope.launch { _errorReceived.emit(error) }
                }
                MessageType.HISTORY_RESPONSE -> {
                    val historyResponse = gson.fromJson(gson.toJson(message.payload), HistoryResponsePayload::class.java)
                    scope.launch { _historyResponse.emit(historyResponse) }
                    Log.d(TAG, "History response received for conversation with ${historyResponse.withUserId}: ${historyResponse.messages.size} messages")
                }
                else -> {
                    Log.w(TAG, "Unknown message type: ${message.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming message", e)
        }
    }

    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    private inner class WebSocketListenerImpl : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened")
            _connectionState.value = ConnectionState.CONNECTED
            startPingPong()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            pingJob?.cancel()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            pingJob?.cancel()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            _connectionState.value = ConnectionState.ERROR
            pingJob?.cancel()

            // Auto-reconnect after delay
            scope.launch {
                delay(5000)
                if (_connectionState.value == ConnectionState.ERROR) {
                    Log.d(TAG, "Attempting to reconnect...")
                    connect()
                }
            }
        }
    }

    fun cleanup() {
        scope.cancel()
        disconnect()
    }
}
