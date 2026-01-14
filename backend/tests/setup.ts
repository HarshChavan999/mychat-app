// Jest setup file for backend tests
import { jest } from '@jest/globals';

// Extend global object for test utilities
declare global {
  var testUtils: {
    createMockWebSocket: () => any;
    createMockMessage: (overrides?: any) => any;
    createMockAuthMessage: (token?: string) => any;
  };
}

// Mock environment variables for test mode
process.env.NODE_ENV = 'test';
process.env.FIREBASE_PROJECT_ID = 'test-project';

// Firebase Admin SDK is handled in auth.ts for test mode
// No additional mocking needed

// Global test utilities
global.testUtils = {
  createMockWebSocket: () => ({
    send: jest.fn(),
    close: jest.fn(),
    on: jest.fn(),
    readyState: 1, // WebSocket.OPEN
  }),

  createMockMessage: (overrides = {}) => ({
    type: 'MESSAGE',
    payload: {
      to: 'user2',
      content: 'Hello World',
    },
    id: 'msg-123',
    timestamp: Date.now(),
    ...overrides,
  }),

  createMockAuthMessage: (token = 'valid-token') => ({
    type: 'AUTH',
    payload: { token },
    timestamp: Date.now(),
  }),
};
