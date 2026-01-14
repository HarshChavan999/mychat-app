import { databaseService } from './database';

// Database row type for users table
type UserDbRow = {
  id: string;
  email: string | null;
  display_name: string;
  avatar_url?: string | null;
  bio?: string | null;
  is_anonymous: boolean;
  is_online: boolean;
  last_seen: string | Date;
  created_at: string | Date;
  updated_at: string | Date;
};

// Enhanced User interface for database operations
export interface DbUser {
  id: string;
  email: string | null;
  display_name: string;
  avatar_url?: string | null;
  bio?: string | null;
  is_anonymous: boolean;
  is_online: boolean;
  last_seen: Date;
  created_at: Date;
  updated_at: Date;
}

// User service class for database operations
export class UserService {

  // Create or update user on authentication
  async createOrUpdateUser(userData: {
    id: string;
    email?: string;
    displayName: string;
    isAnonymous?: boolean;
  }): Promise<DbUser> {
    const { id, email, displayName, isAnonymous = false } = userData;

    const query = `
      INSERT INTO users (id, email, display_name, is_anonymous, is_online, last_seen, updated_at)
      VALUES ($1, $2, $3, $4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON CONFLICT (id)
      DO UPDATE SET
        email = COALESCE(EXCLUDED.email, users.email),
        display_name = EXCLUDED.display_name,
        is_online = true,
        last_seen = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
      RETURNING *
    `;

    const result = await databaseService.query(query, [id, email || null, displayName, isAnonymous]);

    if (result.rows.length === 0) {
      throw new Error('Failed to create or update user');
    }

    return this.mapDbRowToUser(result.rows[0]);
  }

  // Get user by ID
  async getUserById(userId: string): Promise<DbUser | null> {
    const query = 'SELECT * FROM users WHERE id = $1';
    const result = await databaseService.query(query, [userId]);

    if (result.rows.length === 0) {
      return null;
    }

    return this.mapDbRowToUser(result.rows[0]);
  }

  // Update user online status
  async updateUserOnlineStatus(userId: string, isOnline: boolean): Promise<void> {
    const query = `
      UPDATE users
      SET is_online = $1, last_seen = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
      WHERE id = $2
    `;
    await databaseService.query(query, [isOnline, userId]);
  }

  // Update user profile
  async updateUserProfile(userId: string, updates: {
    display_name?: string;
    avatar_url?: string;
    bio?: string;
  }): Promise<DbUser | null> {
    const { display_name, avatar_url, bio } = updates;

    const query = `
      UPDATE users
      SET
        display_name = COALESCE($1, display_name),
        avatar_url = COALESCE($2, avatar_url),
        bio = COALESCE($3, bio),
        updated_at = CURRENT_TIMESTAMP
      WHERE id = $4
      RETURNING *
    `;

    const result = await databaseService.query(query, [display_name, avatar_url, bio, userId]);

    if (result.rows.length === 0) {
      return null;
    }

    return this.mapDbRowToUser(result.rows[0]);
  }

  // Get online users count
  async getOnlineUsersCount(): Promise<number> {
    const query = 'SELECT COUNT(*) as count FROM users WHERE is_online = true';
    const result = await databaseService.query(query);
    return parseInt(result.rows[0].count);
  }

  // Get all online users (for monitoring/admin purposes)
  async getOnlineUsers(limit: number = 100): Promise<DbUser[]> {
    const query = `
      SELECT * FROM users
      WHERE is_online = true
      ORDER BY last_seen DESC
      LIMIT $1
    `;
    const result = await databaseService.query(query, [limit]);
    return result.rows.map((row: any) => this.mapDbRowToUser(row as UserDbRow));
  }

  // Search users by display name (for future friend system)
  async searchUsers(searchTerm: string, limit: number = 20): Promise<DbUser[]> {
    const query = `
      SELECT * FROM users
      WHERE display_name ILIKE $1
      ORDER BY last_seen DESC
      LIMIT $2
    `;
    const result = await databaseService.query(query, [`%${searchTerm}%`, limit]);
    return result.rows.map((row: any) => this.mapDbRowToUser(row as UserDbRow));
  }

  // Update last seen timestamp for user
  async updateLastSeen(userId: string): Promise<void> {
    const query = `
      UPDATE users
      SET last_seen = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
      WHERE id = $1
    `;
    await databaseService.query(query, [userId]);
  }

  // Clean up offline users (mark as offline if not seen recently)
  async cleanupOfflineUsers(maxAgeMinutes: number = 5): Promise<number> {
    const query = `
      UPDATE users
      SET is_online = false, updated_at = CURRENT_TIMESTAMP
      WHERE is_online = true
      AND last_seen < CURRENT_TIMESTAMP - INTERVAL '${maxAgeMinutes} minutes'
    `;
    const result = await databaseService.query(query);
    return result.rowCount || 0;
  }

  // Get user statistics
  async getUserStats(): Promise<{
    totalUsers: number;
    onlineUsers: number;
    anonymousUsers: number;
    recentUsers: number; // Users created in last 24 hours
  }> {
    const queries = [
      'SELECT COUNT(*) as count FROM users',
      'SELECT COUNT(*) as count FROM users WHERE is_online = true',
      'SELECT COUNT(*) as count FROM users WHERE is_anonymous = true',
      "SELECT COUNT(*) as count FROM users WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'"
    ];

    const results = await Promise.all(queries.map(query => databaseService.query(query)));

    return {
      totalUsers: parseInt(results[0].rows[0].count),
      onlineUsers: parseInt(results[1].rows[0].count),
      anonymousUsers: parseInt(results[2].rows[0].count),
      recentUsers: parseInt(results[3].rows[0].count)
    };
  }

  // Helper method to map database row to DbUser interface
  private mapDbRowToUser(row: {
    id: string;
    email: string | null;
    display_name: string;
    avatar_url?: string | null;
    bio?: string | null;
    is_anonymous: boolean;
    is_online: boolean;
    last_seen: string | Date;
    created_at: string | Date;
    updated_at: string | Date;
  }): DbUser {
    return {
      id: row.id,
      email: row.email,
      display_name: row.display_name,
      avatar_url: row.avatar_url,
      bio: row.bio,
      is_anonymous: row.is_anonymous,
      is_online: row.is_online,
      last_seen: new Date(row.last_seen),
      created_at: new Date(row.created_at),
      updated_at: new Date(row.updated_at)
    };
  }
}

// Export singleton instance
export const userService = new UserService();
