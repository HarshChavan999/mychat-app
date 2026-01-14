"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const http = __importStar(require("http"));
const ws_1 = require("ws");
const dotenv = __importStar(require("dotenv"));
const auth_1 = require("./auth");
const messageHandler_1 = require("./messageHandler");
// Load environment variables
dotenv.config();
// Initialize Firebase
(0, auth_1.initializeFirebase)();
const PORT = process.env.PORT || 8080;
const server = http.createServer();
const wss = new ws_1.WebSocket.Server({ server, host: '0.0.0.0' });
const messageHandler = new messageHandler_1.MessageHandler();
// Store authenticated connections
const authenticatedConnections = new Map();
// Clean up old messages every hour
setInterval(() => {
    messageHandler.cleanupOldMessages();
}, 60 * 60 * 1000);
wss.on('connection', (ws) => {
    console.log('New WebSocket connection established');
    // Skip authentication - create a dummy user for development
    const dummyUserId = `guest-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
    authenticatedConnections.set(ws, dummyUserId);
    messageHandler.registerUser(dummyUserId, ws);
    console.log(`Guest user ${dummyUserId} connected (no auth required)`);
    // Set up ping/pong for connection health
    const pingInterval = setInterval(() => {
        if (ws.readyState === 1) { // WebSocket.OPEN
            messageHandler.handlePing(ws);
        }
    }, 30000); // Ping every 30 seconds
    ws.on('message', async (data) => {
        try {
            const message = JSON.parse(data.toString());
            // Get user ID for this connection (now always available)
            const userId = authenticatedConnections.get(ws);
            if (!userId) {
                sendError(ws, 'Connection not registered');
                return;
            }
            switch (message.type) {
                case 'AUTH':
                    // Skip auth - just acknowledge
                    const successMessage = {
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
                    messageHandler.handleAck(message, userId);
                    break;
                case 'PING':
                    messageHandler.handlePing(ws);
                    break;
                default:
                    sendError(ws, `Unknown message type: ${message.type}`);
                    break;
            }
        }
        catch (error) {
            console.error('Error processing message:', error);
            sendError(ws, 'Invalid message format');
        }
    });
    ws.on('close', () => {
        console.log('WebSocket connection closed');
        clearInterval(pingInterval);
        // Remove from authenticated connections
        const userId = authenticatedConnections.get(ws);
        if (userId) {
            authenticatedConnections.delete(ws);
            messageHandler.unregisterUser(userId);
        }
    });
    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
        clearInterval(pingInterval);
        // Clean up on error
        const userId = authenticatedConnections.get(ws);
        if (userId) {
            authenticatedConnections.delete(ws);
            messageHandler.unregisterUser(userId);
        }
    });
});
async function handleAuth(ws, message) {
    const auth = await Promise.resolve().then(() => __importStar(require('./auth')));
    const result = await auth.handleAuthMessage(message);
    if (result) {
        // Check if user is already connected
        const existingConnection = Array.from(authenticatedConnections.entries())
            .find(([_, uid]) => uid === result.userId);
        if (existingConnection) {
            // Close existing connection
            existingConnection[0].close(1000, 'New connection established');
            authenticatedConnections.delete(existingConnection[0]);
            messageHandler.unregisterUser(result.userId);
        }
        // Register new connection
        authenticatedConnections.set(ws, result.userId);
        messageHandler.registerUser(result.userId, ws);
        // Send success response
        const successMessage = {
            type: 'AUTH',
            payload: { success: true, user: result.user },
            timestamp: Date.now()
        };
        ws.send(JSON.stringify(successMessage));
        console.log(`User ${result.userId} authenticated successfully`);
    }
    else {
        // Send failure response
        const failureMessage = {
            type: 'AUTH',
            payload: { success: false, error: 'Authentication failed' },
            timestamp: Date.now()
        };
        ws.send(JSON.stringify(failureMessage));
        ws.close(1008, 'Authentication failed');
    }
}
function sendError(ws, error) {
    const errorMessage = {
        type: 'ERROR',
        payload: { error },
        timestamp: Date.now()
    };
    ws.send(JSON.stringify(errorMessage));
}
// Health check endpoint
server.on('request', (req, res) => {
    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'healthy',
            onlineUsers: messageHandler.getOnlineUsersCount(),
            timestamp: new Date().toISOString()
        }));
    }
    else {
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
//# sourceMappingURL=server.js.map