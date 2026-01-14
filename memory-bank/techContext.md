# Technical Context: MyChat Technology Stack

## Technology Stack Overview

### Frontend: Android Application
**Platform**: Android API 21+ (Android 5.0 Lollipop)
**Language**: Kotlin 1.9+
**UI Framework**: Jetpack Compose
**Architecture**: MVVM with Repository pattern
**Networking**: OkHttp WebSocket client
**Authentication**: Firebase Authentication (email/password, Google, anonymous)

### Backend: Node.js WebSocket Server
**Runtime**: Node.js 18+
**Language**: TypeScript 5.0+
**WebSocket Library**: ws (pure WebSocket implementation)
**Authentication**: Firebase Admin SDK
**Build Tool**: TypeScript compiler
**Testing**: Jest with ts-jest

### Infrastructure: Google Cloud Platform (Free Tier)
**Compute**: Cloud Run (2M requests/month free)
**Container Registry**: Artifact Registry (5GB free)
**Monitoring**: Cloud Logging (50GB logs free)
**CI/CD**: GitHub Actions (free for public repos)

## Development Setup

### Local Development Environment
```bash
# Android Development
- Android Studio / VS Code with Kotlin extensions
- Android SDK API 33 (emulator/device testing)
- Gradle 8.0+ build system
- Firebase CLI for configuration

# Backend Development
- Node.js 18+ with npm
- TypeScript compiler
- Jest testing framework
- nodemon for hot reloading

# Infrastructure
- Docker for containerization
- Google Cloud SDK (gcloud CLI)
- GitHub CLI for repository management
```

### Development Workflow
- **Version Control**: Git with GitHub
- **Code Quality**: ESLint (backend), Android Lint (frontend)
- **Testing**: Unit tests (Jest/Kotlin), Integration tests, UI tests
- **CI/CD**: GitHub Actions for automated testing and deployment
- **Documentation**: Memory Bank system for project knowledge

## Technical Constraints

### Android Platform Constraints
- **API Level**: Minimum API 21 (Android 5.0) for broad compatibility
- **Memory Management**: Mobile resource constraints require efficient memory usage
- **Battery Optimization**: Background processing must minimize battery drain
- **Network Variability**: App must handle WiFi, cellular, and offline scenarios

### Backend Constraints
- **Free Tier Limits**: GCP free tier restrictions (requests, storage, compute)
- **Stateless Design**: No persistent storage, in-memory user management
- **Scalability Target**: Designed for 1000 concurrent users
- **Cost Optimization**: Minimize resource usage to stay within free tiers

### Infrastructure Constraints
- **Zero Cost Requirement**: All infrastructure must remain within free tier limits
- **Global Availability**: Single region deployment (us-central1)
- **Stateless Architecture**: No persistent data storage requirements
- **Monitoring Limitations**: Basic logging without advanced analytics

## Dependencies & Libraries

### Android Dependencies (build.gradle.kts)
```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
```

### Backend Dependencies (package.json)
```json
{
  "dependencies": {
    "ws": "^8.14.0",
    "firebase-admin": "^12.0.0",
    "uuid": "^9.0.0",
    "dotenv": "^16.3.0"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "@types/ws": "^8.5.0",
    "@types/uuid": "^9.0.0",
    "typescript": "^5.0.0",
    "ts-node": "^10.9.0",
    "nodemon": "^3.0.0",
    "jest": "^29.0.0",
    "ts-jest": "^29.0.0",
    "@types/jest": "^29.0.0"
  }
}
```

## Tool Usage Patterns

### Android Development Tools
- **Android Studio**: Primary IDE for Android development and debugging
- **ADB**: Device communication and log monitoring
- **Firebase Console**: Authentication configuration and user management
- **Android Emulator**: Testing across different Android versions and devices

### Backend Development Tools
- **VS Code**: TypeScript development with debugging capabilities
- **Postman**: WebSocket testing and API exploration
- **Jest**: Unit testing with coverage reporting
- **nodemon**: Development server with hot reloading

### Infrastructure Tools
- **Docker**: Containerization for consistent deployment
- **Google Cloud SDK**: GCP resource management and deployment
- **GitHub CLI**: Repository management and release automation
- **GitHub Actions**: CI/CD pipeline execution

## Configuration Management

### Environment Variables
**Backend (.env)**:
```env
PORT=8080
NODE_ENV=development
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_PRIVATE_KEY=your-private-key
FIREBASE_CLIENT_EMAIL=your-service-account-email
```

**Android (gradle.properties)**:
```properties
# Firebase configuration handled via google-services.json
# Build configuration
kotlin.code.style=official
android.useAndroidX=true
android.enableJetifier=true
```

### Build Configuration
**Android (build.gradle.kts)**:
```kotlin
android {
    namespace = "com.example.mychat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mychat"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
}
```

**Backend (tsconfig.json)**:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "commonjs",
    "lib": ["ES2020"],
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "tests"]
}
```

## Development Best Practices

### Code Quality Standards
- **Kotlin**: Official coding style, null safety, data classes
- **TypeScript**: Strict mode, interface definitions, async/await patterns
- **Testing**: Unit test coverage >70% (backend), >60% (Android)
- **Documentation**: Comprehensive code comments and Memory Bank system

### Performance Optimization
- **Android**: Efficient Compose UI updates, memory leak prevention
- **Backend**: Connection pooling, efficient message routing
- **Network**: Minimal data transfer, smart reconnection strategies
- **Battery**: Background processing optimization, network awareness

### Security Practices
- **Authentication**: Firebase Auth for secure user management
- **WebSocket**: Secure connections (wss://), token-based authentication
- **Data Validation**: Input sanitization and type checking
- **Environment Security**: Secure credential management in production

## Testing Strategy

### Unit Testing
- **Android**: JUnit 4/5 with Mockito for dependency mocking
- **Backend**: Jest with ts-jest for TypeScript testing
- **Coverage**: Minimum 70% backend, 60% Android coverage targets

### Integration Testing
- **WebSocket Testing**: Connection lifecycle and message flow validation
- **Authentication Testing**: End-to-end auth flow verification
- **Network Testing**: Offline/online transition simulation

### UI Testing
- **Android**: Compose UI testing framework
- **Backend**: API endpoint testing with supertest
- **End-to-End**: Manual testing across multiple devices

## Deployment Pipeline

### Development Workflow
1. **Local Development**: Hot reloading with nodemon/Android Studio
2. **Testing**: Unit tests, integration tests, manual testing
3. **Code Review**: GitHub pull requests with automated checks
4. **Staging**: Docker container testing in development environment
5. **Production**: Automated deployment to GCP Cloud Run

### CI/CD Configuration (GitHub Actions)
```yaml
# .github/workflows/deploy.yml
name: Deploy Chat App
on: [push, pull_request]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: node-version: '18'
      - run: cd backend && npm ci && npm test

  test-android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: java-version: '17'
      - run: ./gradlew test

  deploy:
    needs: [test-backend, test-android]
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Cloud Run
        run: gcloud run deploy chat-backend --source=./backend
```

### Infrastructure as Code
- **Docker**: Containerized backend deployment
- **Terraform**: Infrastructure configuration (future enhancement)
- **GitHub Actions**: Declarative CI/CD pipeline
- **Environment Management**: .env files with secure credential handling

## Monitoring & Observability

### Development Monitoring
- **Android**: ADB logs, Firebase Crashlytics (future)
- **Backend**: Console logging, Jest test reports
- **Performance**: Android Profiler, Node.js performance monitoring

### Production Monitoring
- **GCP Cloud Logging**: Centralized log aggregation
- **Cloud Run Metrics**: Request latency, error rates, resource usage
- **Custom Metrics**: WebSocket connection counts, message throughput
- **Alerting**: GCP budget alerts, error rate monitoring

### Cost Monitoring
- **GCP Billing**: Real-time cost tracking and budget alerts
- **Resource Usage**: CPU, memory, and network monitoring
- **Efficiency Metrics**: Cost per active user, cost per message
- **Optimization**: Regular review of resource utilization

This technical foundation provides a robust, scalable, and cost-effective platform for real-time messaging while maintaining the flexibility needed for future enhancements and enterprise scaling.
