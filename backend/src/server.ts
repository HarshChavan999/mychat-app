import * as http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import * as dotenv from 'dotenv';
import { initializeFirebase } from './auth';
import { MessageHandler } from './messageHandler';
import { WebSocketMessage } from './types';
import { databaseService } from './database';
import { userService } from './userService';
import { messageService } from './messageService';

// Load environment variables
dotenv.config();

// Initialize Firebase
initializeFirebase();

// Initialize database
(async () => {
  try {
    await databaseService.initialize();
    console.log('Database initialized successfully');
  } catch (error) {
    console.error('Failed to initialize database:', error);
    process.exit(1);
  }
})();

const PORT = process.env.PORT || 8080;
const server = http.createServer();
const wss = new WebSocket.Server({ server, host: '0.0.0.0' });
const messageHandler = new MessageHandler();

// Store authenticated connections
const authenticatedConnections = new Map<WebSocket, string>();

// Clean up old messages every hour
setInterval(() => {
  messageHandler.cleanupOldMessages();
}, 60 * 60 * 1000);

wss.on('connection', async (ws: WebSocket) => {
  console.log('New WebSocket connection established');

  // Skip authentication - create a dummy user for development
  const dummyUserId = `guest-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

  try {
    // Create user in database first
    const dbUser = await userService.createOrUpdateUser({
      id: dummyUserId,
      displayName: `Guest_${dummyUserId.substring(6, 14)}`,
      isAnonymous: true
    });

    authenticatedConnections.set(ws, dummyUserId);
    await messageHandler.registerUser(dummyUserId, ws);

    console.log(`Guest user ${dummyUserId} created in database and connected (no auth required)`);
  } catch (error) {
    console.error('Failed to create guest user in database:', error);
    ws.close(1011, 'Database error');
    return;
  }

  // Set up ping/pong for connection health
  const pingInterval = setInterval(() => {
    if (ws.readyState === 1) { // WebSocket.OPEN
      messageHandler.handlePing(ws);
    }
  }, 30000); // Ping every 30 seconds

  ws.on('message', async (data: Buffer) => {
    try {
      const message: WebSocketMessage = JSON.parse(data.toString());

      // Get user ID for this connection (now always available)
      const userId = authenticatedConnections.get(ws);

      if (!userId) {
        sendError(ws, 'Connection not registered');
        return;
      }

      switch (message.type) {
        case 'AUTH':
          // Skip auth - just acknowledge
          const successMessage: WebSocketMessage = {
            type: 'AUTH',
            payload: { success: true, user: { id: userId, displayName: `Guest_${userId.substring(6, 14)}` } },
            timestamp: Date.now()
          };
          ws.send(JSON.stringify(successMessage));
          break;

        case 'MESSAGE':
          await messageHandler.handleMessage(message, userId);
          break;

        case 'ACK':
          await messageHandler.handleAck(message, userId);
          break;

        case 'HISTORY_REQUEST':
          await messageHandler.handleHistoryRequest(message, userId);
          break;

        case 'PING':
          messageHandler.handlePing(ws);
          break;

        default:
          sendError(ws, `Unknown message type: ${message.type}`);
          break;
      }
    } catch (error) {
      console.error('Error processing message:', error);
      sendError(ws, 'Invalid message format');
    }
  });

  ws.on('close', async () => {
    console.log('WebSocket connection closed');
    clearInterval(pingInterval);

    // Remove from authenticated connections
    const userId = authenticatedConnections.get(ws);
    if (userId) {
      authenticatedConnections.delete(ws);
      await messageHandler.unregisterUser(userId);
    }
  });

  ws.on('error', async (error) => {
    console.error('WebSocket error:', error);
    clearInterval(pingInterval);

    // Clean up on error
    const userId = authenticatedConnections.get(ws);
    if (userId) {
      authenticatedConnections.delete(ws);
      await messageHandler.unregisterUser(userId);
    }
  });
});

async function handleAuth(ws: WebSocket, message: WebSocketMessage) {
  const auth = await import('./auth');
  const result = await auth.handleAuthMessage(message);

  if (result) {
    try {
      // Create or update user in database
      const dbUser = await userService.createOrUpdateUser({
        id: result.userId,
        email: result.user.email,
        displayName: result.user.displayName,
        isAnonymous: result.user.isAnonymous
      });

      // Check if user is already connected
      const existingConnection = Array.from(authenticatedConnections.entries())
        .find(([_, uid]) => uid === result.userId);

      if (existingConnection) {
        // Close existing connection
        existingConnection[0].close(1000, 'New connection established');
        authenticatedConnections.delete(existingConnection[0]);
        await messageHandler.unregisterUser(result.userId);
      }

      // Register new connection
      authenticatedConnections.set(ws, result.userId);
      await messageHandler.registerUser(result.userId, ws);

      // Send success response with database user info
      const successMessage: WebSocketMessage = {
        type: 'AUTH',
        payload: {
          success: true,
          user: {
            id: dbUser.id,
            displayName: dbUser.display_name,
            email: dbUser.email,
            avatar_url: dbUser.avatar_url,
            bio: dbUser.bio,
            is_online: dbUser.is_online,
            last_seen: dbUser.last_seen.toISOString()
          }
        },
        timestamp: Date.now()
      };
      ws.send(JSON.stringify(successMessage));

      console.log(`User ${result.userId} authenticated and stored in database successfully`);
    } catch (error) {
      console.error('Database error during authentication:', error);
      const failureMessage: WebSocketMessage = {
        type: 'AUTH',
        payload: { success: false, error: 'Database error during authentication' },
        timestamp: Date.now()
      };
      ws.send(JSON.stringify(failureMessage));
      ws.close(1011, 'Database error');
    }
  } else {
    // Send failure response
    const failureMessage: WebSocketMessage = {
      type: 'AUTH',
      payload: { success: false, error: 'Authentication failed' },
      timestamp: Date.now()
    };
    ws.send(JSON.stringify(failureMessage));
    ws.close(1008, 'Authentication failed');
  }
}

function sendError(ws: WebSocket, error: string) {
  const errorMessage: WebSocketMessage = {
    type: 'ERROR',
    payload: { error },
    timestamp: Date.now()
  };
  ws.send(JSON.stringify(errorMessage));
}

// Health check endpoints
server.on('request', async (req, res) => {
  if (req.url === '/health') {
    // Basic health check - always returns healthy
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'healthy',
      uptime: process.uptime(),
      timestamp: new Date().toISOString()
    }));
  } else if (req.url === '/ready') {
    // Readiness check - verifies database and services are ready
    try {
      // Test database connection
      await databaseService.query('SELECT 1');

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 'ready',
        onlineUsers: messageHandler.getOnlineUsersCount(),
        database: 'connected',
        timestamp: new Date().toISOString()
      }));
    } catch (error) {
      console.error('Readiness check failed:', error);
      res.writeHead(503, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 'not ready',
        error: 'Database connection failed',
        timestamp: new Date().toISOString()
      }));
    }
  } else {
    res.writeHead(404);
    res.end('Not Found');
  }
});

server.listen(PORT, () => {
  console.log(`WebSocket server running on port ${PORT}`);
  console.log(`Health check available at http://localhost:${PORT}/health`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('Shutting down server...');
  wss.close(() => {
    server.close(() => {
      process.exit(0);
    });
  });
});

process.on('SIGINT', () => {
  console.log('Shutting down server...');
  wss.close(() => {
    server.close(() => {
      process.exit(0);
    });
  });
});
