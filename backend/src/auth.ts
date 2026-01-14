import * as admin from 'firebase-admin';
import { WebSocketMessage, AuthPayload } from './types';

let firebaseApp: admin.app.App | null = null;

export function initializeFirebase() {
  // In development mode with dummy Firebase config, skip Firebase initialization
  if (process.env.NODE_ENV === 'development' && process.env.FIREBASE_PROJECT_ID === 'your-chat-app-project') {
    console.log('Development mode: Skipping Firebase initialization');
    return null;
  }

  if (!firebaseApp) {
    // Initialize Firebase Admin SDK
    // In production, load credentials from environment variables or service account file
    firebaseApp = admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID || 'your-chat-app-project'
    });
  }
  return firebaseApp;
}

export async function verifyFirebaseToken(token: string): Promise<admin.auth.DecodedIdToken | null> {
  // In development mode with dummy Firebase config, accept any token as valid
  if (process.env.NODE_ENV === 'development' && process.env.FIREBASE_PROJECT_ID === 'your-chat-app-project') {
    console.log('Development mode: Accepting token without Firebase verification');
    // Return a mock decoded token for development
    return {
      uid: `dev-user-${Date.now()}`,
      email: 'dev@example.com',
      name: 'Development User',
      firebase: {
        sign_in_provider: token.includes('anonymous') ? 'anonymous' : 'password'
      }
    } as any;
  }

  try {
    const app = initializeFirebase();
    if (!app) {
      console.error('Firebase app not initialized');
      return null;
    }
    const decodedToken = await app.auth().verifyIdToken(token);
    return decodedToken;
  } catch (error) {
    console.error('Error verifying Firebase token:', error);
    return null;
  }
}

export async function handleAuthMessage(message: WebSocketMessage): Promise<{ userId: string; user: any } | null> {
  const payload = message.payload as AuthPayload;

  if (!payload || !payload.token) {
    console.error('Invalid AUTH message: missing token');
    return null;
  }

  const decodedToken = await verifyFirebaseToken(payload.token);

  if (!decodedToken) {
    console.error('Invalid Firebase token');
    return null;
  }

  // Handle both regular and anonymous Firebase users
  const isAnonymous = decodedToken.firebase.sign_in_provider === 'anonymous';

  // Extract user information from the decoded token
  const user = {
    id: decodedToken.uid,
    email: decodedToken.email || '',
    displayName: decodedToken.name || decodedToken.email || (isAnonymous ? `Anonymous_${decodedToken.uid.substring(0, 8)}` : 'Anonymous'),
    isAnonymous: isAnonymous
  };

  return { userId: user.id, user };
}
