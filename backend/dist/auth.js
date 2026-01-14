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
exports.initializeFirebase = initializeFirebase;
exports.verifyFirebaseToken = verifyFirebaseToken;
exports.handleAuthMessage = handleAuthMessage;
const admin = __importStar(require("firebase-admin"));
let firebaseApp = null;
function initializeFirebase() {
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
async function verifyFirebaseToken(token) {
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
        };
    }
    try {
        const app = initializeFirebase();
        if (!app) {
            console.error('Firebase app not initialized');
            return null;
        }
        const decodedToken = await app.auth().verifyIdToken(token);
        return decodedToken;
    }
    catch (error) {
        console.error('Error verifying Firebase token:', error);
        return null;
    }
}
async function handleAuthMessage(message) {
    const payload = message.payload;
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
//# sourceMappingURL=auth.js.map