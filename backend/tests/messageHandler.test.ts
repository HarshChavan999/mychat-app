import { MessageHandler } from '../src/messageHandler';
import { WebSocketMessage, MessagePayload, AckPayload } from '../src/types';

// Mock WebSocket
const mockWebSocket = {
  send: jest.fn(),
  close: jest.fn(),
  readyState: 1,
};

describe('MessageHandler', () => {
  let messageHandler: MessageHandler;
  let mockWs1: any;
  let mockWs2: any;

  beforeEach(() => {
    messageHandler = new MessageHandler();
    mockWs1 = { ...mockWebSocket, send: jest.fn() };
    mockWs2 = { ...mockWebSocket, send: jest.fn() };
    jest.clearAllMocks();
  });

  describe('User Registration', () => {
    it('should register a user successfully', () => {
      const result = messageHandler.registerUser('user1', mockWs1);

      expect(result).toBe(true);
    });

    it('should unregister a user successfully', () => {
      messageHandler.registerUser('user1', mockWs1);
      const result = messageHandler.unregisterUser('user1');

      expect(result).toBe(true);
    });

    it('should handle registering same user with different socket (replace)', () => {
      messageHandler.registerUser('user1', mockWs1);
      const result = messageHandler.registerUser('user1', mockWs2);

      expect(result).toBe(true);
      // Should close the old connection
      expect(mockWs1.close).toHaveBeenCalled();
    });

    it('should return false when unregistering non-existent user', () => {
      const result = messageHandler.unregisterUser('nonexistent');

      expect(result).toBe(false);
    });
  });

  describe('Message Handling', () => {
    beforeEach(() => {
      messageHandler.registerUser('user1', mockWs1);
      messageHandler.registerUser('user2', mockWs2);
    });

    it('should handle MESSAGE type correctly', async () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user2',
          content: 'Hello from user1',
        } as MessagePayload,
        id: 'msg-123',
        timestamp: Date.now(),
      };

      const result = await messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(true);
      const sentData = mockWs2.send.mock.calls[0][0];
      const parsedData = JSON.parse(sentData);
      expect(parsedData.type).toBe('MESSAGE');
      expect(parsedData.payload.id).toBe('msg-123');
      expect(parsedData.payload.from).toBe('user1');
      expect(parsedData.payload.content).toBe('Hello from user1');
      expect(typeof parsedData.payload.timestamp).toBe('number');
      expect(typeof parsedData.timestamp).toBe('number');
    });

    it('should send message to self (for demo purposes)', async () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user1', // Same as sender
          content: 'Self message',
        } as MessagePayload,
        id: 'msg-124',
        timestamp: Date.now(),
      };

      const result = await messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(true);
      // Message is delivered to self, and ACK is sent
      expect(mockWs1.send).toHaveBeenCalledTimes(2); // MESSAGE + ACK
      expect(mockWs2.send).not.toHaveBeenCalled();
    });

    it('should return false for message to non-existent user', async () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'nonexistent',
          content: 'Message to nowhere',
        } as MessagePayload,
        id: 'msg-125',
        timestamp: Date.now(),
      };

      const result = await messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(true); // Message is processed and queued, even if not delivered
      expect(mockWs1.send).toHaveBeenCalled(); // ACK should be sent
      expect(mockWs2.send).not.toHaveBeenCalled();
    });

    it('should handle invalid message payload', async () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {} as any, // Invalid payload
        timestamp: Date.now(),
      };

      const result = await messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(false);
    });
  });

  describe('ACK Handling', () => {
    it('should handle ACK message correctly', async () => {
      // First send a message to an offline user to create a pending message
      messageHandler.registerUser('user1', mockWs1);
      // user2 is not registered (offline)

      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user2', // Offline user
          content: 'Hello',
        } as MessagePayload,
        id: 'msg-123',
        timestamp: Date.now(),
      };

      await messageHandler.handleMessage(message, 'user1');

      // Now register user2 and have them ACK the message
      messageHandler.registerUser('user2', mockWs2);

      const ackMessage: WebSocketMessage = {
        type: 'ACK',
        payload: {
          messageId: 'msg-123',
        } as AckPayload,
        timestamp: Date.now(),
      };

      const result = messageHandler.handleAck(ackMessage, 'user2'); // ACK from recipient

      expect(result).toBe(true);
    });

    it('should handle invalid ACK payload', () => {
      const ackMessage: WebSocketMessage = {
        type: 'ACK',
        payload: {} as any, // Invalid payload
        timestamp: Date.now(),
      };

      const result = messageHandler.handleAck(ackMessage, 'user1');

      expect(result).toBe(false);
    });
  });

  describe('Ping/Pong Handling', () => {
    it('should handle PING message and respond with PONG', () => {
      messageHandler.registerUser('user1', mockWs1);

      messageHandler.handlePing(mockWs1);

      const sentData = mockWs1.send.mock.calls[0][0];
      const parsedData = JSON.parse(sentData);
      expect(parsedData.type).toBe('PONG');
      expect(typeof parsedData.timestamp).toBe('number');
    });

    it('should handle PING for non-registered user', () => {
      const unregisteredWs = { ...mockWebSocket, send: jest.fn() } as any;

      messageHandler.handlePing(unregisteredWs);

      const sentData = unregisteredWs.send.mock.calls[0][0];
      const parsedData = JSON.parse(sentData);
      expect(parsedData.type).toBe('PONG');
      expect(typeof parsedData.timestamp).toBe('number');
    });
  });

  describe('Connection Statistics', () => {
    it('should return correct online user count', () => {
      expect(messageHandler.getOnlineUsersCount()).toBe(0);

      messageHandler.registerUser('user1', mockWs1);
      expect(messageHandler.getOnlineUsersCount()).toBe(1);

      messageHandler.registerUser('user2', mockWs2);
      expect(messageHandler.getOnlineUsersCount()).toBe(2);

      messageHandler.unregisterUser('user1');
      expect(messageHandler.getOnlineUsersCount()).toBe(1);
    });

    it('should handle multiple registrations of same user', () => {
      messageHandler.registerUser('user1', mockWs1 as any);
      messageHandler.registerUser('user2', mockWs2 as any);
      expect(messageHandler.getOnlineUsersCount()).toBe(2);

      // Register user1 again with different socket
      const mockWs3 = { ...mockWebSocket, send: jest.fn(), close: jest.fn() };
      messageHandler.registerUser('user1', mockWs3 as any);
      expect(messageHandler.getOnlineUsersCount()).toBe(2); // Still 2 users
    });
  });

  describe('Message Cleanup', () => {
    it('should clean up old messages', () => {
      // Mock Date.now to control time
      const originalNow = Date.now;
      const mockNow = jest.fn();
      Date.now = mockNow;

      // Set initial time
      mockNow.mockReturnValue(1000);

      // Register users and send messages
      messageHandler.registerUser('user1', mockWs1);
      messageHandler.registerUser('user2', mockWs2);

      // This would normally clean up messages older than some threshold
      messageHandler.cleanupOldMessages();

      // Restore original Date.now
      Date.now = originalNow;
    });
  });

  describe('Error Handling', () => {
    it('should handle malformed messages gracefully', () => {
      const malformedMessage = {
        type: 'INVALID_TYPE',
        payload: null,
      } as any;

      // Should not crash
      expect(() => {
        messageHandler.handleMessage(malformedMessage, 'user1');
      }).not.toThrow();

      expect(() => {
        messageHandler.handleAck(malformedMessage, 'user1');
      }).not.toThrow();
    });

    it('should handle WebSocket send errors gracefully', async () => {
      mockWs1.send.mockImplementation(() => {
        throw new Error('WebSocket error');
      });

      messageHandler.registerUser('user1', mockWs1);

      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user1',
          content: 'Test',
        } as MessagePayload,
        id: 'msg-123',
        timestamp: Date.now(),
      };

      // Should not crash even if WebSocket send fails
      await expect(messageHandler.handleMessage(message, 'user2')).rejects.toThrow('WebSocket error');
    });
  });
});
