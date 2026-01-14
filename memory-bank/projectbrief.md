# Project Brief: MyChat - WhatsApp-like Real-time Chat Application

## Core Mission
Build a fully functional WhatsApp-like chat application that demonstrates enterprise-grade real-time messaging capabilities with zero infrastructure costs for up to 1000 users.

## Key Requirements

### Functional Requirements
- **Real-time messaging**: Instant message delivery between authenticated users
- **Firebase Authentication**: Support for email/password, Google, and anonymous authentication
- **Message delivery confirmation**: Visual indicators for SENT and DELIVERED states
- **Offline resilience**: Message queuing during network interruptions with automatic retry
- **Cross-platform compatibility**: Android client with Node.js backend via WebSocket protocol

### Technical Requirements
- **Zero cost infrastructure**: Utilize GCP free tier (Cloud Run, Compute Engine, Artifact Registry)
- **Clean architecture**: MVVM pattern on Android, modular Node.js backend
- **WebSocket protocol**: Custom protocol implementation (AUTH, MESSAGE, ACK, PING)
- **Production readiness**: Error handling, logging, health checks, and monitoring
- **Scalability foundation**: Designed to handle 1000+ users with future database integration

### Quality Standards
- **Test coverage**: >70% backend, >60% Android unit tests
- **Performance**: <100ms message latency, efficient memory management
- **Reliability**: 99.9% uptime through intelligent connection management
- **User experience**: Seamless offline/online transitions with automatic message recovery

## Success Criteria
- [x] End-to-end message flow working between two authenticated users
- [x] Real-time delivery with proper ACK handling
- [x] Offline message queuing with automatic processing on reconnection
- [x] Network-aware connection management
- [x] Comprehensive error handling and user feedback
- [ ] Automated CI/CD deployment pipeline
- [ ] Production deployment to GCP Cloud Run
- [ ] Android APK build and distribution
- [ ] Complete test suite execution

## Project Scope
**In Scope:**
- Real-time messaging between authenticated users
- Firebase authentication integration
- Offline message queuing and retry logic
- Network connectivity monitoring
- WebSocket protocol implementation
- MVVM Android architecture
- Node.js WebSocket server with Firebase Admin SDK

**Out of Scope (Future Phases):**
- Group chats and multi-user rooms
- Message persistence with database
- Push notifications via FCM
- End-to-end encryption
- File/image sharing
- Voice/video messaging

## Technical Constraints
- **Cost**: Must remain within GCP free tier limits
- **Platform**: Android API 21+ compatibility
- **Performance**: Memory-efficient for mobile devices
- **Security**: Firebase handles authentication, WebSocket connections secure
- **Scalability**: In-memory storage suitable for initial 1000 users

## Development Approach
- **CLI-first development**: All setup and deployment via command line tools
- **Iterative implementation**: Core features first, enhancements second
- **Test-driven development**: Comprehensive testing at each layer
- **Clean code principles**: Maintainable, documented, and well-structured codebase
