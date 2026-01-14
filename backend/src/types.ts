export interface WebSocketMessage {
  type: 'AUTH' | 'MESSAGE' | 'ACK' | 'PING' | 'PONG' | 'ERROR';
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

export interface Message {
  id: string;
  from: string;
  to: string;
  content: string;
  timestamp: number;
  status: 'SENT' | 'DELIVERED';
}

export interface User {
  id: string;
  email: string;
  displayName: string;
}
