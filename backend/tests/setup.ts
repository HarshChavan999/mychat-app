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

// Mock Firebase Admin SDK
const mockVerifyIdToken = jest.fn() as jest.MockedFunction<any>;
const mockInitializeApp = jest.fn() as jest.MockedFunction<any>;

jest.mock('firebase-admin', () => ({
  initializeApp: mockInitializeApp,
  auth: jest.fn(() => ({
    verifyIdToken: mockVerifyIdToken,
  })),
  credential: {
    applicationDefault: jest.fn(),
  },
}));

// Export mocks for use in tests
export { mockVerifyIdToken, mockInitializeApp };

// Mock environment variables
process.env.NODE_ENV = 'test';
process.env.FIREBASE_PROJECT_ID = 'test-project';

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
