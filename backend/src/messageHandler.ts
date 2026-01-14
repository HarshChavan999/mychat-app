import { v4 as uuidv4 } from 'uuid';
import { WebSocket } from 'ws';
import { WebSocketMessage, MessagePayload, AckPayload, Message, HistoryRequestPayload, HistoryResponsePayload } from './types';
import { messageService } from './messageService';
import { userService } from './userService';

export class MessageHandler {
  private onlineUsers: Map<string, WebSocket> = new Map();
  private pendingMessages: Map<string, Message> = new Map(); // Keep for backward compatibility, but also store in DB

  // Register a user as online
  async registerUser(userId: string, ws: WebSocket): Promise<boolean> {
    // If user was already connected, close the old connection
    const existingWs = this.onlineUsers.get(userId);
    if (existingWs && existingWs !== ws) {
      existingWs.close();
    }
    this.onlineUsers.set(userId, ws);

    // Update user status in database
    await userService.updateUserOnlineStatus(userId, true);

    console.log(`User ${userId} connected. Online users: ${this.onlineUsers.size}`);

    // Deliver any offline messages
    await this.deliverOfflineMessages(userId);

    return true;
  }

  // Unregister a user when they disconnect
  async unregisterUser(userId: string): Promise<boolean> {
    const existed = this.onlineUsers.delete(userId);
    if (existed) {
      console.log(`User ${userId} disconnected. Online users: ${this.onlineUsers.size}`);

      // Update user status in database to offline
      try {
        await userService.updateUserOnlineStatus(userId, false);
      } catch (error) {
        console.error(`Error updating offline status for user ${userId}:`, error);
      }
    }
    return existed;
  }

  // Check if a user is online
  isUserOnline(userId: string): boolean {
    return this.onlineUsers.has(userId);
  }

  // Handle incoming MESSAGE type
  async handleMessage(message: WebSocketMessage, fromUserId: string): Promise<boolean> {
    const payload = message.payload as MessagePayload;

    if (!payload || !payload.to || !payload.content) {
      console.error('Invalid MESSAGE payload');
      return false;
    }

    const messageId = message.id || uuidv4();
    const timestamp = message.timestamp || Date.now();

    // LOG MESSAGE TO PC CONSOLE
    console.log(`📱 MESSAGE RECEIVED: [${new Date(timestamp).toLocaleString()}] ${fromUserId} -> ${payload.to}: "${payload.content}"`);

    try {
      // Store message in database
      await messageService.storeMessage({
        id: messageId,
        fromUserId,
        toUserId: payload.to,
        content: payload.content,
        timestamp
      });

      // Update last seen for sender
      await userService.updateLastSeen(fromUserId);

      // Try to deliver message if recipient is online
      const delivered = await this.deliverMessageToRecipient(messageId, fromUserId, payload.to, payload.content, timestamp);

      if (delivered) {
        // Mark message as delivered in database
        await messageService.updateMessageStatus(messageId, 'DELIVERED');
        console.log(`✅ Message delivered to ${payload.to}`);
      } else {
        // Special handling for demo user - echo the message back
        if (payload.to === 'demo-user-123') {
          console.log(`🎯 Demo user received message: "${payload.content}"`);
          await this.echoMessageToSender(messageId, fromUserId, payload.to, payload.content, timestamp);
        } else {
          console.log(`📨 Message stored for offline user ${payload.to}`);
        }
      }

      // Send ACK back to sender
      const senderWs = this.onlineUsers.get(fromUserId);
      if (senderWs) {
        const ackMessage: WebSocketMessage = {
          type: 'ACK',
          payload: { messageId },
          timestamp: Date.now()
        };
        senderWs.send(JSON.stringify(ackMessage));
        console.log(`📤 ACK sent to ${fromUserId} for message ${messageId}`);
      }

      return true;
    } catch (error) {
      console.error('Error handling message:', error);
      return false;
    }
  }

  // Deliver message to recipient (new method for database integration)
  private async deliverMessageToRecipient(messageId: string, fromUserId: string, toUserId: string, content: string, timestamp: number): Promise<boolean> {
    const recipientWs = this.onlineUsers.get(toUserId);

    if (!recipientWs) {
      console.log(`User ${toUserId} is not online. Message stored in database.`);
      return false;
    }

    try {
      const wsMessage: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          id: messageId,
          from: fromUserId,
          content: content,
          timestamp: timestamp
        },
        timestamp: Date.now()
      };

      recipientWs.send(JSON.stringify(wsMessage));
      return true;
    } catch (error) {
      console.error('Error delivering message:', error);
      return false;
    }
  }

  // Deliver message to recipient (legacy method for backward compatibility)
  private async deliverMessage(message: Message): Promise<boolean> {
    return this.deliverMessageToRecipient(message.id, message.from, message.to, message.content, message.timestamp);
  }

  // Handle ACK from recipient
  async handleAck(message: WebSocketMessage, fromUserId: string): Promise<boolean> {
    const payload = message.payload as AckPayload;

    if (!payload || !payload.messageId) {
      console.error('Invalid ACK payload');
      return false;
    }

    try {
      // Update message status in database to DELIVERED
      const updatedMessage = await messageService.updateMessageStatus(payload.messageId, 'DELIVERED');

      if (updatedMessage) {
        // Update last seen for recipient
        await userService.updateLastSeen(fromUserId);
        console.log(`Message ${payload.messageId} acknowledged by ${fromUserId}`);
        return true;
      }

      return false;
    } catch (error) {
      console.error('Error handling ACK:', error);
      return false;
    }
  }

  // Echo message back to sender (for demo user) - updated signature
  private async echoMessageToSender(messageId: string, senderId: string, demoUserId: string, originalContent: string, timestamp: number): Promise<void> {
    const senderWs = this.onlineUsers.get(senderId);
    if (!senderWs) {
      console.log(`Cannot echo message: sender ${senderId} not connected`);
      return;
    }

    // Create echo response and store in database
    const echoMessageId = uuidv4();
    const echoContent = `Echo: ${originalContent}`;

    try {
      // Store echo message in database
      await messageService.storeMessage({
        id: echoMessageId,
        fromUserId: demoUserId,
        toUserId: senderId,
        content: echoContent,
        timestamp: Date.now()
      });

      // Mark as delivered since we're sending it immediately
      await messageService.updateMessageStatus(echoMessageId, 'DELIVERED');

      // Send echo message to sender
      const wsMessage: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          id: echoMessageId,
          from: demoUserId,
          content: echoContent,
          timestamp: Date.now()
        },
        timestamp: Date.now()
      };

      senderWs.send(JSON.stringify(wsMessage));
      console.log(`🔄 ECHO SENT: ${demoUserId} -> ${senderId}: "${echoContent}"`);
    } catch (error) {
      console.error('Error sending echo message:', error);
    }
  }

  // Handle HISTORY_REQUEST messages
  async handleHistoryRequest(message: WebSocketMessage, fromUserId: string): Promise<boolean> {
    const payload = message.payload as HistoryRequestPayload;

    if (!payload || !payload.withUserId) {
      console.error('Invalid HISTORY_REQUEST payload');
      return false;
    }

    try {
      const limit = payload.limit || 50;
      const beforeTimestamp = payload.beforeTimestamp ? new Date(payload.beforeTimestamp) : undefined;

      // Get conversation history from database
      const messages = await messageService.getConversation(fromUserId, payload.withUserId, limit, beforeTimestamp);

      // Check if there are more messages (simple check: if we got the limit, assume there are more)
      const hasMore = messages.length === limit;

      // Convert DbMessage[] to Message[] for response
      const responseMessages: Message[] = messages.map(dbMsg => ({
        id: dbMsg.id,
        from: dbMsg.from_user_id,
        to: dbMsg.to_user_id,
        content: dbMsg.content,
        timestamp: dbMsg.timestamp.getTime(),
        status: dbMsg.status
      }));

      // Send history response back to user
      const userWs = this.onlineUsers.get(fromUserId);
      if (userWs) {
        const historyResponse: WebSocketMessage = {
          type: 'HISTORY_RESPONSE',
          payload: {
            withUserId: payload.withUserId,
            messages: responseMessages,
            hasMore
          } as HistoryResponsePayload,
          timestamp: Date.now()
        };

        userWs.send(JSON.stringify(historyResponse));
        console.log(`📚 Sent ${responseMessages.length} messages from history to ${fromUserId} (conversation with ${payload.withUserId})`);
      }

      return true;
    } catch (error) {
      console.error('Error handling history request:', error);
      return false;
    }
  }

  // Handle PING messages
  handlePing(ws: WebSocket): void {
    const pongMessage: WebSocketMessage = {
      type: 'PONG',
      timestamp: Date.now()
    };
    ws.send(JSON.stringify(pongMessage));
  }

  // Deliver offline messages to a user when they come online
  private async deliverOfflineMessages(userId: string): Promise<void> {
    try {
      const offlineMessages = await messageService.getUndeliveredMessagesForUser(userId);

      if (offlineMessages.length > 0) {
        console.log(`📨 Delivering ${offlineMessages.length} offline messages to ${userId}`);

        const userWs = this.onlineUsers.get(userId);
        if (!userWs) {
          console.log(`User ${userId} disconnected before offline messages could be delivered`);
          return;
        }

        // Send each offline message
        for (const message of offlineMessages) {
          try {
            const wsMessage: WebSocketMessage = {
              type: 'MESSAGE',
              payload: {
                id: message.id,
                from: message.from_user_id,
                content: message.content,
                timestamp: message.timestamp.getTime()
              },
              timestamp: Date.now()
            };

            userWs.send(JSON.stringify(wsMessage));

            // Mark message as delivered
            await messageService.updateMessageStatus(message.id, 'DELIVERED');
            console.log(`✅ Delivered offline message ${message.id} to ${userId}`);
          } catch (error) {
            console.error(`Failed to deliver offline message ${message.id}:`, error);
          }
        }
      }
    } catch (error) {
      console.error(`Error delivering offline messages to ${userId}:`, error);
    }
  }

  // Get online users count (for monitoring)
  getOnlineUsersCount(): number {
    return this.onlineUsers.size;
  }

  // Clean up pending messages older than specified time
  cleanupOldMessages(maxAgeMs: number = 24 * 60 * 60 * 1000): void {
    const now = Date.now();
    const toDelete: string[] = [];

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
