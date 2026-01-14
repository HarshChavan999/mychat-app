"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MessageHandler = void 0;
const uuid_1 = require("uuid");
class MessageHandler {
    constructor() {
        this.onlineUsers = new Map();
        this.pendingMessages = new Map();
    }
    // Register a user as online
    registerUser(userId, ws) {
        this.onlineUsers.set(userId, ws);
        console.log(`User ${userId} connected. Online users: ${this.onlineUsers.size}`);
    }
    // Unregister a user when they disconnect
    unregisterUser(userId) {
        this.onlineUsers.delete(userId);
        console.log(`User ${userId} disconnected. Online users: ${this.onlineUsers.size}`);
    }
    // Check if a user is online
    isUserOnline(userId) {
        return this.onlineUsers.has(userId);
    }
    // Handle incoming MESSAGE type
    async handleMessage(message, fromUserId) {
        const payload = message.payload;
        if (!payload || !payload.to || !payload.content) {
            console.error('Invalid MESSAGE payload');
            return;
        }
        // Create message object
        const messageObj = {
            id: message.id || (0, uuid_1.v4)(),
            from: fromUserId,
            to: payload.to,
            content: payload.content,
            timestamp: message.timestamp || Date.now(),
            status: 'SENT'
        };
        // LOG MESSAGE TO PC CONSOLE
        console.log(`📱 MESSAGE RECEIVED: [${new Date(messageObj.timestamp).toLocaleString()}] ${fromUserId} -> ${payload.to}: "${payload.content}"`);
        // Store message temporarily
        this.pendingMessages.set(messageObj.id, messageObj);
        // Try to deliver message
        const delivered = await this.deliverMessage(messageObj);
        if (delivered) {
            messageObj.status = 'DELIVERED';
            this.pendingMessages.delete(messageObj.id);
            console.log(`✅ Message delivered to ${payload.to}`);
        }
        else {
            // Special handling for demo user - echo the message back
            if (payload.to === 'demo-user-123') {
                console.log(`🎯 Demo user received message: "${payload.content}"`);
                // Echo the message back to the sender
                await this.echoMessageToSender(messageObj, fromUserId);
                this.pendingMessages.delete(messageObj.id);
            }
            else {
                console.log(`📨 Message queued for offline user ${payload.to}`);
            }
        }
        // Send ACK back to sender
        const senderWs = this.onlineUsers.get(fromUserId);
        if (senderWs) {
            const ackMessage = {
                type: 'ACK',
                payload: { messageId: messageObj.id },
                timestamp: Date.now()
            };
            senderWs.send(JSON.stringify(ackMessage));
            console.log(`📤 ACK sent to ${fromUserId} for message ${messageObj.id}`);
        }
    }
    // Deliver message to recipient
    async deliverMessage(message) {
        const recipientWs = this.onlineUsers.get(message.to);
        if (!recipientWs) {
            console.log(`User ${message.to} is not online. Message queued.`);
            return false;
        }
        try {
            const wsMessage = {
                type: 'MESSAGE',
                payload: {
                    id: message.id,
                    from: message.from,
                    content: message.content,
                    timestamp: message.timestamp
                },
                timestamp: Date.now()
            };
            recipientWs.send(JSON.stringify(wsMessage));
            return true;
        }
        catch (error) {
            console.error('Error delivering message:', error);
            return false;
        }
    }
    // Handle ACK from recipient
    handleAck(message, fromUserId) {
        const payload = message.payload;
        if (!payload || !payload.messageId) {
            console.error('Invalid ACK payload');
            return;
        }
        // Remove message from pending if it exists
        const pendingMessage = this.pendingMessages.get(payload.messageId);
        if (pendingMessage && pendingMessage.from === fromUserId) {
            this.pendingMessages.delete(payload.messageId);
            console.log(`Message ${payload.messageId} acknowledged by ${fromUserId}`);
        }
    }
    // Echo message back to sender (for demo user)
    async echoMessageToSender(originalMessage, senderId) {
        const senderWs = this.onlineUsers.get(senderId);
        if (!senderWs) {
            console.log(`Cannot echo message: sender ${senderId} not connected`);
            return;
        }
        // Create echo response
        const echoMessage = {
            id: (0, uuid_1.v4)(),
            from: originalMessage.to, // From demo user
            to: senderId,
            content: `Echo: ${originalMessage.content}`, // Echo the original message
            timestamp: Date.now(),
            status: 'DELIVERED'
        };
        try {
            const wsMessage = {
                type: 'MESSAGE',
                payload: {
                    id: echoMessage.id,
                    from: echoMessage.from,
                    content: echoMessage.content,
                    timestamp: echoMessage.timestamp
                },
                timestamp: Date.now()
            };
            senderWs.send(JSON.stringify(wsMessage));
            console.log(`🔄 ECHO SENT: demo-user-123 -> ${senderId}: "${echoMessage.content}"`);
        }
        catch (error) {
            console.error('Error sending echo message:', error);
        }
    }
    // Handle PING messages
    handlePing(ws) {
        const pongMessage = {
            type: 'PONG',
            timestamp: Date.now()
        };
        ws.send(JSON.stringify(pongMessage));
    }
    // Get online users count (for monitoring)
    getOnlineUsersCount() {
        return this.onlineUsers.size;
    }
    // Clean up pending messages older than specified time
    cleanupOldMessages(maxAgeMs = 24 * 60 * 60 * 1000) {
        const now = Date.now();
        const toDelete = [];
        for (const [id, message] of this.pendingMessages) {
            if (now - message.timestamp > maxAgeMs) {
                toDelete.push(id);
            }
        }
        toDelete.forEach(id => this.pendingMessages.delete(id));
        if (toDelete.length > 0) {
            console.log(`Cleaned up ${toDelete.length} old pending messages`);
        }
    }
}
exports.MessageHandler = MessageHandler;
//# sourceMappingURL=messageHandler.js.map