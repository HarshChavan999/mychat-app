import { databaseService } from './database';

// Enhanced Message interface for database operations
export interface DbMessage {
  id: string;
  from_user_id: string;
  to_user_id: string;
  content: string;
  timestamp: Date;
  status: 'SENT' | 'DELIVERED' | 'READ';
  delivered_at?: Date | null;
  created_at: Date;
}

// Message service class for database operations
export class MessageService {

  // Store a new message
  async storeMessage(message: {
    id: string;
    fromUserId: string;
    toUserId: string;
    content: string;
    timestamp?: number;
  }): Promise<DbMessage> {
    const { id, fromUserId, toUserId, content, timestamp = Date.now() } = message;

    const query = `
      INSERT INTO messages (id, from_user_id, to_user_id, content, timestamp, status)
      VALUES ($1, $2, $3, $4, $5, 'SENT')
      RETURNING *
    `;

    const result = await databaseService.query(query, [id, fromUserId, toUserId, content, new Date(timestamp)]);

    if (result.rows.length === 0) {
      throw new Error('Failed to store message');
    }

    return this.mapDbRowToMessage(result.rows[0]);
  }

  // Update message status
  async updateMessageStatus(messageId: string, status: 'DELIVERED' | 'READ'): Promise<DbMessage | null> {
    let updateFields = 'status = $1, updated_at = CURRENT_TIMESTAMP';
    let params: any[] = [status];

    if (status === 'DELIVERED') {
      updateFields += ', delivered_at = CURRENT_TIMESTAMP';
    }

    const query = `
      UPDATE messages
      SET ${updateFields}
      WHERE id = $${params.length + 1}
      RETURNING *
    `;

    params.push(messageId);
    const result = await databaseService.query(query, params);

    if (result.rows.length === 0) {
      return null;
    }

    return this.mapDbRowToMessage(result.rows[0]);
  }

  // Get message by ID
  async getMessageById(messageId: string): Promise<DbMessage | null> {
    const query = 'SELECT * FROM messages WHERE id = $1';
    const result = await databaseService.query(query, [messageId]);

    if (result.rows.length === 0) {
      return null;
    }

    return this.mapDbRowToMessage(result.rows[0]);
  }

  // Get conversation between two users (with pagination)
  async getConversation(
    userId1: string,
    userId2: string,
    limit: number = 50,
    beforeTimestamp?: Date
  ): Promise<DbMessage[]> {
    let query = `
      SELECT * FROM messages
      WHERE (from_user_id = $1 AND to_user_id = $2) OR (from_user_id = $2 AND to_user_id = $1)
      ORDER BY timestamp DESC
      LIMIT $3
    `;

    const params: any[] = [userId1, userId2, limit];

    if (beforeTimestamp) {
      query = query.replace('ORDER BY timestamp DESC', 'AND timestamp < $4 ORDER BY timestamp DESC');
      params.push(beforeTimestamp);
    }

    const result = await databaseService.query(query, params);
    return result.rows.map((row: any) => this.mapDbRowToMessage(row)).reverse(); // Return in chronological order
  }

  // Get messages for a user (recent conversations)
  async getRecentMessagesForUser(userId: string, limit: number = 100): Promise<DbMessage[]> {
    const query = `
      SELECT * FROM messages
      WHERE from_user_id = $1 OR to_user_id = $1
      ORDER BY timestamp DESC
      LIMIT $2
    `;

    const result = await databaseService.query(query, [userId, limit]);
    return result.rows.map((row: any) => this.mapDbRowToMessage(row));
  }

  // Get undelivered messages for a user (for offline message delivery)
  async getUndeliveredMessagesForUser(userId: string): Promise<DbMessage[]> {
    const query = `
      SELECT * FROM messages
      WHERE to_user_id = $1 AND status = 'SENT'
      ORDER BY timestamp ASC
    `;

    const result = await databaseService.query(query, [userId]);
    return result.rows.map((row: any) => this.mapDbRowToMessage(row));
  }

  // Mark all messages from sender to recipient as delivered
  async markMessagesAsDelivered(fromUserId: string, toUserId: string): Promise<number> {
    const query = `
      UPDATE messages
      SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP
      WHERE from_user_id = $1 AND to_user_id = $2 AND status = 'SENT'
    `;

    const result = await databaseService.query(query, [fromUserId, toUserId]);
    return result.rowCount || 0;
  }

  // Get message statistics
  async getMessageStats(): Promise<{
    totalMessages: number;
    deliveredMessages: number;
    readMessages: number;
    pendingMessages: number;
    messagesLast24h: number;
  }> {
    const queries = [
      'SELECT COUNT(*) as count FROM messages',
      'SELECT COUNT(*) as count FROM messages WHERE status = \'DELIVERED\'',
      'SELECT COUNT(*) as count FROM messages WHERE status = \'READ\'',
      'SELECT COUNT(*) as count FROM messages WHERE status = \'SENT\'',
      "SELECT COUNT(*) as count FROM messages WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours'"
    ];

    const results = await Promise.all(queries.map(query => databaseService.query(query)));

    return {
      totalMessages: parseInt(results[0].rows[0].count),
      deliveredMessages: parseInt(results[1].rows[0].count),
      readMessages: parseInt(results[2].rows[0].count),
      pendingMessages: parseInt(results[3].rows[0].count),
      messagesLast24h: parseInt(results[4].rows[0].count)
    };
  }

  // Get conversation partners for a user (for chat list)
  async getConversationPartners(userId: string, limit: number = 20): Promise<{
    partnerId: string;
    lastMessage: DbMessage;
    unreadCount: number;
  }[]> {
    // This query gets the most recent message for each conversation partner
    const query = `
      WITH latest_messages AS (
        SELECT
          CASE
            WHEN from_user_id = $1 THEN to_user_id
            ELSE from_user_id
          END as partner_id,
          *,
          ROW_NUMBER() OVER (
            PARTITION BY
              CASE
                WHEN from_user_id = $1 THEN to_user_id
                ELSE from_user_id
              END
            ORDER BY timestamp DESC
          ) as rn
        FROM messages
        WHERE from_user_id = $1 OR to_user_id = $1
      ),
      unread_counts AS (
        SELECT
          from_user_id as sender_id,
          COUNT(*) as unread_count
        FROM messages
        WHERE to_user_id = $1 AND status = 'SENT'
        GROUP BY from_user_id
      )
      SELECT
        lm.*,
        COALESCE(uc.unread_count, 0) as unread_count
      FROM latest_messages lm
      LEFT JOIN unread_counts uc ON uc.sender_id = lm.partner_id
      WHERE lm.rn = 1
      ORDER BY lm.timestamp DESC
      LIMIT $2
    `;

    const result = await databaseService.query(query, [userId, limit]);

    return result.rows.map((row: any) => ({
      partnerId: row.partner_id,
      lastMessage: this.mapDbRowToMessage(row),
      unreadCount: parseInt(row.unread_count)
    }));
  }

  // Delete old messages (for cleanup, optional feature)
  async deleteOldMessages(olderThanDays: number = 90): Promise<number> {
    const query = `
      DELETE FROM messages
      WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '${olderThanDays} days'
    `;

    const result = await databaseService.query(query);
    return result.rowCount || 0;
  }

  // Helper method to map database row to DbMessage interface
  private mapDbRowToMessage(row: any): DbMessage {
    return {
      id: row.id,
      from_user_id: row.from_user_id,
      to_user_id: row.to_user_id,
      content: row.content,
      timestamp: new Date(row.timestamp),
      status: row.status,
      delivered_at: row.delivered_at ? new Date(row.delivered_at) : null,
      created_at: new Date(row.created_at)
    };
  }
}

// Export singleton instance
export const messageService = new MessageService();
