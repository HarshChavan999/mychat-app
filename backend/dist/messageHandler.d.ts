import { WebSocket } from 'ws';
import { WebSocketMessage } from './types';
export declare class MessageHandler {
    private onlineUsers;
    private pendingMessages;
    registerUser(userId: string, ws: WebSocket): void;
    unregisterUser(userId: string): void;
    isUserOnline(userId: string): boolean;
    handleMessage(message: WebSocketMessage, fromUserId: string): Promise<void>;
    private deliverMessage;
    handleAck(message: WebSocketMessage, fromUserId: string): void;
    private echoMessageToSender;
    handlePing(ws: WebSocket): void;
    getOnlineUsersCount(): number;
    cleanupOldMessages(maxAgeMs?: number): void;
}
//# sourceMappingURL=messageHandler.d.ts.map