import { Pool, PoolClient } from 'pg';

// Database configuration interface
interface DatabaseConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
  max: number;
  idleTimeoutMillis: number;
  connectionTimeoutMillis: number;
}

// Database service class
export class DatabaseService {
  private pool: Pool;
  private isInitialized = false;

  constructor() {
    this.pool = this.createPool();
    this.setupPoolEventHandlers();
  }

  // Create database connection pool
  private createPool(): Pool {
    const config: DatabaseConfig = {
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '5432'),
      database: process.env.DB_NAME || 'mychat',
      user: process.env.DB_USER || 'postgres',
      password: process.env.DB_PASSWORD || '',
      max: parseInt(process.env.DB_MAX_CONNECTIONS || '20'), // Maximum connections in pool
      idleTimeoutMillis: parseInt(process.env.DB_IDLE_TIMEOUT || '30000'), // Close idle connections after 30s
      connectionTimeoutMillis: parseInt(process.env.DB_CONNECTION_TIMEOUT || '2000'), // Connection timeout
    };

    return new Pool(config);
  }

  // Setup pool event handlers for monitoring
  private setupPoolEventHandlers(): void {
    this.pool.on('connect', (client: PoolClient) => {
      console.log('New database client connected');
    });

    this.pool.on('error', (err: Error, client: PoolClient) => {
      console.error('Unexpected database error:', err);
    });

    this.pool.on('remove', (client: PoolClient) => {
      console.log('Database client removed from pool');
    });
  }

  // Initialize database schema
  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      await this.createTables();
      await this.createIndexes();
      await this.seedDemoUser();
      this.isInitialized = true;
      console.log('Database initialized successfully');
    } catch (error) {
      console.error('Failed to initialize database:', error);
      throw error;
    }
  }

  // Create database tables
  private async createTables(): Promise<void> {
    const client = await this.pool.connect();

    try {
      await client.query('BEGIN');

      // Users table
      await client.query(`
        CREATE TABLE IF NOT EXISTS users (
          id VARCHAR(255) PRIMARY KEY,
          email VARCHAR(255) UNIQUE,
          display_name VARCHAR(255) NOT NULL,
          avatar_url VARCHAR(500),
          bio TEXT,
          is_anonymous BOOLEAN DEFAULT FALSE,
          is_online BOOLEAN DEFAULT FALSE,
          last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )
      `);

      // Messages table
      await client.query(`
        CREATE TABLE IF NOT EXISTS messages (
          id VARCHAR(255) PRIMARY KEY,
          from_user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          to_user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          content TEXT NOT NULL,
          timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
          status VARCHAR(20) DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'READ')),
          delivered_at TIMESTAMP WITH TIME ZONE,
          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )
      `);

      // User relationships table (for future friend system)
      await client.query(`
        CREATE TABLE IF NOT EXISTS user_relationships (
          id SERIAL PRIMARY KEY,
          user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          related_user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          relationship_type VARCHAR(20) DEFAULT 'FRIEND' CHECK (relationship_type IN ('FRIEND', 'BLOCKED')),
          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
          UNIQUE(user_id, related_user_id)
        )
      `);

      await client.query('COMMIT');
      console.log('Database tables created successfully');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  // Create database indexes for performance
  private async createIndexes(): Promise<void> {
    const client = await this.pool.connect();

    try {
      // Indexes for messages table
      await client.query('CREATE INDEX IF NOT EXISTS idx_messages_from_user ON messages(from_user_id)');
      await client.query('CREATE INDEX IF NOT EXISTS idx_messages_to_user ON messages(to_user_id)');
      await client.query('CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp DESC)');
      await client.query('CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status)');
      await client.query('CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id), timestamp DESC)');

      // Indexes for users table
      await client.query('CREATE INDEX IF NOT EXISTS idx_users_online ON users(is_online) WHERE is_online = true');
      await client.query('CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(last_seen DESC)');

      console.log('Database indexes created successfully');
    } finally {
      client.release();
    }
  }

  // Seed demo user for testing
  private async seedDemoUser(): Promise<void> {
    try {
      // Check if demo user already exists
      const existingUser = await this.query('SELECT id FROM users WHERE id = $1', ['demo-user-123']);

      if (existingUser.rows.length === 0) {
        // Create demo user
        await this.query(`
          INSERT INTO users (id, display_name, is_anonymous, is_online, bio)
          VALUES ($1, $2, $3, $4, $5)
        `, ['demo-user-123', 'Demo Bot', true, false, 'A demo user that echoes messages back to you.']);

        console.log('Demo user seeded successfully');
      } else {
        console.log('Demo user already exists');
      }
    } catch (error) {
      console.error('Error seeding demo user:', error);
      throw error;
    }
  }

  // Execute a query with optional parameters
  async query(text: string, params?: any[]): Promise<any> {
    const client = await this.pool.connect();
    try {
      const result = await client.query(text, params);
      return result;
    } finally {
      client.release();
    }
  }

  // Execute a query within a transaction
  async transaction<T>(callback: (client: PoolClient) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await callback(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  // Get pool statistics
  getPoolStats() {
    return {
      totalCount: this.pool.totalCount,
      idleCount: this.pool.idleCount,
      waitingCount: this.pool.waitingCount,
    };
  }

  // Close all connections
  async close(): Promise<void> {
    await this.pool.end();
    console.log('Database connection pool closed');
  }

  // Health check
  async healthCheck(): Promise<boolean> {
    try {
      await this.query('SELECT 1');
      return true;
    } catch (error) {
      console.error('Database health check failed:', error);
      return false;
    }
  }
}

// Export singleton instance
export const databaseService = new DatabaseService();
