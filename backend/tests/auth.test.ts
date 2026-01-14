import { handleAuthMessage, initializeFirebase, verifyFirebaseToken } from '../src/auth';
import * as admin from 'firebase-admin';
import { mockVerifyIdToken, mockInitializeApp } from './setup';

describe('Authentication Module', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Reset Firebase initialization state
    (global as any).firebaseApp = undefined;
  });

  describe('initializeFirebase', () => {
    it('should initialize Firebase app when not already initialized', () => {
      initializeFirebase();

      expect(admin.initializeApp).toHaveBeenCalledTimes(1);
      expect(admin.initializeApp).toHaveBeenCalledWith({
        credential: admin.credential.applicationDefault(),
        projectId: process.env.FIREBASE_PROJECT_ID,
      });
    });

    it('should not reinitialize if already initialized', () => {
      initializeFirebase();
      initializeFirebase(); // Second call

      expect(admin.initializeApp).toHaveBeenCalledTimes(1);
    });
  });

  describe('verifyFirebaseToken', () => {
    beforeEach(() => {
      initializeFirebase();
    });

    it('should successfully verify a valid token', async () => {
      const mockDecodedToken = {
        uid: 'user123',
        email: 'user@example.com',
        name: 'Test User',
      };

      mockVerifyIdToken.mockResolvedValue(mockDecodedToken);

      const result = await verifyFirebaseToken('valid-token');

      expect(result).toEqual(mockDecodedToken);
      expect(mockVerifyIdToken).toHaveBeenCalledWith('valid-token');
    });

    it('should return null for invalid token', async () => {
      mockVerifyIdToken.mockRejectedValue(new Error('Invalid token'));

      const result = await verifyFirebaseToken('invalid-token');

      expect(result).toBeNull();
      expect(mockVerifyIdToken).toHaveBeenCalledWith('invalid-token');
    });

    it('should return null when token verification fails', async () => {
      mockVerifyIdToken.mockRejectedValue(new Error('Token expired'));

      const result = await verifyFirebaseToken('expired-token');

      expect(result).toBeNull();
    });
  });

  describe('handleAuthMessage', () => {
    beforeEach(() => {
      initializeFirebase();
    });

    it('should successfully authenticate with valid token', async () => {
      const mockDecodedToken = {
        uid: 'user123',
        email: 'user@example.com',
        name: 'Test User',
      };

      mockVerifyIdToken.mockResolvedValue(mockDecodedToken);

      const message = testUtils.createMockAuthMessage('valid-token');
      const result = await handleAuthMessage(message);

      expect(result).toEqual({
        userId: 'user123',
        user: {
          id: 'user123',
          email: 'user@example.com',
          displayName: 'Test User',
        },
      });
    });

    it('should handle token with no name field', async () => {
      const mockDecodedToken = {
        uid: 'user123',
        email: 'user@example.com',
        // No name field
      };

      mockVerifyIdToken.mockResolvedValue(mockDecodedToken);

      const message = testUtils.createMockAuthMessage('valid-token');
      const result = await handleAuthMessage(message);

      expect(result).toEqual({
        userId: 'user123',
        user: {
          id: 'user123',
          email: 'user@example.com',
          displayName: 'user@example.com', // Falls back to email
        },
      });
    });

    it('should handle token with no email field', async () => {
      const mockDecodedToken = {
        uid: 'user123',
        // No email field
        name: 'Test User',
      };

      mockVerifyIdToken.mockResolvedValue(mockDecodedToken);

      const message = testUtils.createMockAuthMessage('valid-token');
      const result = await handleAuthMessage(message);

      expect(result).toEqual({
        userId: 'user123',
        user: {
          id: 'user123',
          email: '',
          displayName: 'Test User',
        },
      });
    });

    it('should return null for invalid token', async () => {
      mockVerifyIdToken.mockRejectedValue(new Error('Invalid token'));

      const message = testUtils.createMockAuthMessage('invalid-token');
      const result = await handleAuthMessage(message);

      expect(result).toBeNull();
    });

    it('should return null for message without token', async () => {
      const message = {
        type: 'AUTH' as const,
        payload: {}, // No token
        timestamp: Date.now(),
      };

      const result = await handleAuthMessage(message as any);
      expect(result).toBeNull();
    });

    it('should return null for message without payload', async () => {
      const message = {
        type: 'AUTH' as const,
        // No payload
        timestamp: Date.now(),
      };

      const result = await handleAuthMessage(message as any);
      expect(result).toBeNull();
    });

    it('should handle Firebase errors gracefully', async () => {
      mockVerifyIdToken.mockRejectedValue(new Error('Firebase error'));

      const message = testUtils.createMockAuthMessage('error-token');
      const result = await handleAuthMessage(message);

      expect(result).toBeNull();
    });
  });
});
