export interface WebSocketMessage {
  type: 'AUTH' | 'MESSAGE' | 'ACK' | 'PING' | 'PONG' | 'ERROR' | 'HISTORY_REQUEST' | 'HISTORY_RESPONSE';
  payload?: any;
  id?: string;
  timestamp?: number;
}

export interface AuthPayload {
  token: string;
}

export interface MessagePayload {
  to: string;
  content: string;
}

export interface AckPayload {
  messageId: string;
}

export interface HistoryRequestPayload {
  withUserId: string;
  limit?: number;
  beforeTimestamp?: number;
}

export interface HistoryResponsePayload {
  withUserId: string;
  messages: Message[];
  hasMore: boolean;
}

export interface Message {
  id: string;
  from: string;
  to: string;
  content: string;
  timestamp: number;
  status: 'SENT' | 'DELIVERED' | 'READ';
}

export interface User {
  id: string;
  email: string;
  displayName: string;
}
