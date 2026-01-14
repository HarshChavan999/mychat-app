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

    it('should handle MESSAGE type correctly', () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user2',
          content: 'Hello from user1',
        } as MessagePayload,
        id: 'msg-123',
        timestamp: Date.now(),
      };

      const result = messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(true);
      expect(mockWs2.send).toHaveBeenCalledWith(
        JSON.stringify({
          type: 'MESSAGE',
          payload: {
            id: 'msg-123',
            from: 'user1',
            to: 'user2',
            content: 'Hello from user1',
            timestamp: expect.any(Number),
          },
          timestamp: expect.any(Number),
        })
      );
    });

    it('should not send message to sender', () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'user1', // Same as sender
          content: 'Self message',
        } as MessagePayload,
        id: 'msg-124',
        timestamp: Date.now(),
      };

      const result = messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(true);
      expect(mockWs1.send).not.toHaveBeenCalled();
      expect(mockWs2.send).not.toHaveBeenCalled();
    });

    it('should return false for message to non-existent user', () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {
          to: 'nonexistent',
          content: 'Message to nowhere',
        } as MessagePayload,
        id: 'msg-125',
        timestamp: Date.now(),
      };

      const result = messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(false);
      expect(mockWs1.send).not.toHaveBeenCalled();
      expect(mockWs2.send).not.toHaveBeenCalled();
    });

    it('should handle invalid message payload', () => {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        payload: {} as any, // Invalid payload
        timestamp: Date.now(),
      };

      const result = messageHandler.handleMessage(message, 'user1');

      expect(result).toBe(false);
    });
  });

  describe('ACK Handling', () => {
    it('should handle ACK message correctly', () => {
      const ackMessage: WebSocketMessage = {
        type: 'ACK',
        payload: {
          messageId: 'msg-123',
        } as AckPayload,
        timestamp: Date.now(),
      };

      const result = messageHandler.handleAck(ackMessage, 'user1');

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

      expect(mockWs1.send).toHaveBeenCalledWith(
        JSON.stringify({
          type: 'PONG',
          timestamp: expect.any(Number),
        })
      );
    });

    it('should handle PING for non-registered user', () => {
      const unregisteredWs = { ...mockWebSocket, send: jest.fn() } as any;

      messageHandler.handlePing(unregisteredWs);

      expect(unregisteredWs.send).toHaveBeenCalledWith(
        JSON.stringify({
          type: 'PONG',
          timestamp: expect.any(Number),
        })
      );
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

    it('should handle WebSocket send errors gracefully', () => {
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
      expect(() => {
        messageHandler.handleMessage(message, 'user2');
      }).not.toThrow();
    });
  });
});
