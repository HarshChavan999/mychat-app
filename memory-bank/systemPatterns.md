# System Patterns: MyChat Architecture

## System Architecture Overview

### High-Level Architecture
```
┌─────────────────┐    WebSocket    ┌─────────────────┐
│   Android App   │◄──────────────►│  Node.js Server │
│                 │                │                 │
│ • Jetpack Compose│                │ • WebSocket/ws  │
│ • MVVM Pattern   │                │ • Firebase Admin│
│ • OkHttp WebSocket│                │ • In-Memory DB │
│ • Firebase Auth  │                │ • Message Router│
└─────────────────┘                └─────────────────┘
         │                                   │
         └─────────────┬─────────────┘
                       │
                ┌─────────────────┐
                │   Firebase Auth │
                │                 │
                │ • User Auth     │
                │ • Token Verify  │
                └─────────────────┘
```

### Component Relationships
- **Authentication Flow**: Android App → Firebase Auth → Node.js Server (token verification)
- **Message Flow**: Android App → WebSocket → Node.js Server → WebSocket → Android App
- **Network Monitoring**: Android ConnectivityManager → WebSocketManager → Message Queue
- **State Management**: ViewModel → Repository → WebSocketManager → LiveData/StateFlow

## Key Technical Decisions

### 1. WebSocket Protocol Design
**Decision**: Custom WebSocket protocol over Socket.io
**Rationale**:
- Full control over message types and serialization
- Lighter weight, no additional library dependencies
- Better performance for mobile clients
- Easier debugging and monitoring

**Protocol Structure**:
```typescript
interface WebSocketMessage {
  type: 'AUTH' | 'MESSAGE' | 'ACK' | 'PING';
  payload: AuthPayload | MessagePayload | AckPayload | PingPayload;
  timestamp: number;
  messageId?: string;
}
```

### 2. Android Architecture: MVVM with Repository Pattern
**Decision**: MVVM + Repository pattern for clean architecture
**Rationale**:
- Separation of concerns between UI, business logic, and data
- Testable components with clear responsibilities
- Reactive UI updates with LiveData/StateFlow
- Scalable foundation for future features

**Layer Responsibilities**:
```
UI Layer (Compose) ← ViewModel ← Repository ← Data Source (WebSocket)
    ↓                        ↓            ↓                ↓
User Interaction        State Mgmt    Data Access    Network I/O
```

### 3. Offline-First Message Queuing
**Decision**: Client-side message queuing with automatic retry
**Rationale**:
- Zero message loss during network interruptions
- Seamless user experience regardless of connectivity
- Battery-efficient retry strategies
- Foundation for offline messaging features

**Queue Management Pattern**:
```kotlin
// Message queue with automatic processing
private val messageQueue = mutableListOf<QueuedMessage>()

// Network restoration triggers queue processing
connectivityManager.registerNetworkCallback(object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        processMessageQueue()
    }
})
```

### 4. Network-Aware Connection Management
**Decision**: ConnectivityManager integration with lifecycle awareness
**Rationale**:
- Real-time network state monitoring (WiFi/Cellular/None)
- Intelligent reconnection strategies based on network type
- Battery optimization through network-aware behavior
- Graceful degradation during poor connectivity

**Connection Strategy Pattern**:
```kotlin
enum class NetworkState { WIFI, CELLULAR, NONE }

class WebSocketManager {
    fun handleNetworkChange(newState: NetworkState) {
        when (newState) {
            WIFI -> reconnectImmediately()
            CELLULAR -> reconnectWithDelay()
            NONE -> queueMessages()
        }
    }
}
```

## Design Patterns in Use

### 1. Observer Pattern - Reactive UI Updates
**Implementation**: LiveData/StateFlow for message state management
**Location**: `ChatViewModel.kt`, `AuthViewModel.kt`
**Purpose**: Automatic UI updates when message status changes

```kotlin
class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun sendMessage(content: String) {
        // Update UI immediately
        _messages.update { it + Message(content, MessageStatus.SENDING) }
        // Then send via WebSocket
        webSocketManager.sendMessage(content)
    }
}
```

### 2. Repository Pattern - Data Access Abstraction
**Implementation**: Repository layer between ViewModel and data sources
**Location**: `ChatRepository.kt`, `AuthRepository.kt`
**Purpose**: Single source of truth, testable data operations

```kotlin
class ChatRepository(private val webSocketManager: WebSocketManager) {
    fun sendMessage(message: Message): Flow<MessageStatus> = flow {
        emit(MessageStatus.SENDING)
        webSocketManager.sendMessage(message)
        emit(MessageStatus.SENT)
    }
}
```

### 3. Strategy Pattern - Connection Management
**Implementation**: Different reconnection strategies based on network state
**Location**: `WebSocketManager.kt`
**Purpose**: Adaptable connection behavior for different network conditions

```kotlin
interface ReconnectionStrategy {
    fun shouldReconnect(networkState: NetworkState): Boolean
    fun getReconnectionDelay(attemptCount: Int): Long
}

class WifiReconnectionStrategy : ReconnectionStrategy {
    override fun shouldReconnect(networkState: NetworkState) = networkState == WIFI
    override fun getReconnectionDelay(attemptCount: Int) = 0L // Immediate
}
```

### 4. Factory Pattern - WebSocket Message Creation
**Implementation**: Centralized message creation with validation
**Location**: `WebSocketMessage.kt` companion objects
**Purpose**: Consistent message formatting and type safety

```kotlin
data class WebSocketMessage(val type: MessageType, val payload: Any) {
    companion object {
        fun auth(token: String) = WebSocketMessage(AUTH, AuthPayload(token))
        fun message(content: String, to: String) = WebSocketMessage(MESSAGE, MessagePayload(content, to))
        fun ack(messageId: String) = WebSocketMessage(ACK, AckPayload(messageId))
    }
}
```

## Component Relationships

### Android App Architecture
```
MainActivity
    ↓
NavHost (LoginScreen ↔ ChatScreen)
    ↓
ViewModels (AuthViewModel, ChatViewModel)
    ↓
Repositories (AuthRepository, ChatRepository)
    ↓
Managers (WebSocketManager, NetworkConnectivityManager)
    ↓
Firebase Auth + OkHttp WebSocket
```

### Backend Architecture
```
HTTP Server (Port 8080)
    ↓
WebSocket Server (/ws endpoint)
    ↓
Authentication Middleware (Firebase token verification)
    ↓
Message Router (user-based routing)
    ↓
In-Memory User Store (Map<userId, WebSocket>)
```

### Critical Implementation Paths

#### 1. Message Send Flow
```
User Types Message → Compose UI → ChatViewModel.sendMessage() → ChatRepository → WebSocketManager → Message Queue/Network → Backend Router → Recipient WebSocket → Recipient ViewModel → UI Update
```

#### 2. Authentication Flow
```
User Login → Firebase Auth → AuthViewModel → AuthRepository → Token Retrieval → WebSocketManager.connect() → Backend AUTH Message → Firebase Verification → User Mapping → Success/Failure Response
```

#### 3. Network Recovery Flow
```
Network Lost → ConnectivityManager → WebSocketManager.onNetworkLost() → Queue Messages → Network Restored → ConnectivityManager → WebSocketManager.onNetworkAvailable() → Reconnect → Process Queue → Send Pending Messages
```

#### 4. ACK Processing Flow
```
Message Sent → Backend Receives → Routes to Recipient → Recipient ACKs → Backend Forwards ACK → Sender WebSocketManager → Updates Message Status → ViewModel → UI Updates Status Indicator
```

## Data Flow Patterns

### Unidirectional Data Flow
```
User Action → UI Event → ViewModel → Repository → Data Source → Network
                                                        ↓
Network Response → Data Source → Repository → ViewModel → UI Update
```

### Message State Management
```
Message Created → Status: SENDING → WebSocket Send → Status: SENT → ACK Received → Status: DELIVERED
```

### Connection State Management
```
Disconnected → Connecting → Connected → Network Lost → Reconnecting → Connected
```

## Error Handling Patterns

### Network Error Handling
- **Connection Failures**: Exponential backoff reconnection
- **Timeout Errors**: Automatic retry with different strategies
- **Network Changes**: Graceful reconnection with queue processing

### Authentication Error Handling
- **Invalid Tokens**: Automatic token refresh and re-authentication
- **Expired Sessions**: User logout and re-login flow
- **Permission Errors**: Clear error messages and recovery options

### Message Error Handling
- **Send Failures**: Queue for retry, user notifications
- **Delivery Failures**: Status updates, retry mechanisms
- **ACK Timeouts**: Fallback status updates

## Performance Optimization Patterns

### Memory Management
- **Message Cleanup**: Automatic removal of old messages from memory
- **Connection Pooling**: Efficient WebSocket connection reuse
- **Lazy Loading**: UI components loaded on demand

### Network Efficiency
- **Message Batching**: Group small messages when possible
- **Connection Keep-alive**: PING/PONG for connection maintenance
- **Smart Reconnection**: Network-aware reconnection timing

### Battery Optimization
- **Background Processing**: Efficient background message handling
- **Network Awareness**: Different behavior for WiFi vs Cellular
- **Resource Cleanup**: Proper cleanup on app lifecycle changes

## Testing Patterns

### Unit Testing
- **ViewModel Testing**: Business logic testing with mocked repositories
- **Repository Testing**: Data operation testing with mocked network
- **Manager Testing**: WebSocket and network logic testing

### Integration Testing
- **WebSocket Testing**: Connection lifecycle and message flow testing
- **Authentication Testing**: End-to-end auth flow testing
- **Network Testing**: Offline/online transition testing

### UI Testing
- **Compose Testing**: UI component behavior testing
- **Screen Flow Testing**: Navigation and state changes testing
- **User Interaction Testing**: Message sending and receiving flows

## Scalability Considerations

### Current Limitations (In-Memory Storage)
- **User Limit**: ~1000 concurrent users (memory constraints)
- **Message History**: No persistence, messages lost on server restart
- **Horizontal Scaling**: Single server instance only

### Future Scaling Patterns
- **Database Integration**: PostgreSQL for message persistence
- **Redis Clustering**: Distributed user session management
- **Load Balancing**: Multiple server instances with message routing
- **CDN Integration**: Static asset delivery optimization

This architecture provides a solid foundation for real-time messaging while maintaining the simplicity and cost-effectiveness required for the initial 1000-user target, with clear paths for future scaling to enterprise levels.
