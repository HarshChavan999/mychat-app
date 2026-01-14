import { handleAuthMessage, initializeFirebase, verifyFirebaseToken } from '../src/auth';

describe('Authentication Module', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('initializeFirebase', () => {
    it('should return null in test mode', () => {
      const result = initializeFirebase();
      expect(result).toBeNull();
    });
  });

  describe('verifyFirebaseToken', () => {
    it('should return mock token in test mode', async () => {
      const result = await verifyFirebaseToken('any-token');

      expect(result).not.toBeNull();
      expect(result?.uid).toMatch(/^test-user-/);
      expect(result?.email).toBe('test@example.com');
      expect(result?.name).toBe('Test User');
      expect(result?.firebase.sign_in_provider).toBe('password');
    });

    it('should handle anonymous tokens', async () => {
      const result = await verifyFirebaseToken('anonymous-token');

      expect(result).not.toBeNull();
      expect(result?.firebase.sign_in_provider).toBe('anonymous');
    });
  });

  describe('handleAuthMessage', () => {
    it('should successfully authenticate with valid token', async () => {
      const message = {
        type: 'AUTH' as const,
        payload: { token: 'valid-token' },
        timestamp: Date.now(),
      };

      const result = await handleAuthMessage(message);

      expect(result).not.toBeNull();
      expect(result?.userId).toMatch(/^test-user-/);
      expect(result?.user.email).toBe('test@example.com');
      expect(result?.user.displayName).toBe('Test User');
      expect(result?.user.isAnonymous).toBe(false);
    });

    it('should handle anonymous authentication', async () => {
      const message = {
        type: 'AUTH' as const,
        payload: { token: 'anonymous-token' },
        timestamp: Date.now(),
      };

      const result = await handleAuthMessage(message);

      expect(result).not.toBeNull();
      expect(result?.user.isAnonymous).toBe(true);
      // Since our mock returns the same user for all tokens, check that it's not undefined
      expect(result?.user.displayName).toBeDefined();
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
  });
});
