import * as admin from 'firebase-admin';
import { WebSocketMessage } from './types';
export declare function initializeFirebase(): admin.app.App | null;
export declare function verifyFirebaseToken(token: string): Promise<admin.auth.DecodedIdToken | null>;
export declare function handleAuthMessage(message: WebSocketMessage): Promise<{
    userId: string;
    user: any;
} | null>;
//# sourceMappingURL=auth.d.ts.map