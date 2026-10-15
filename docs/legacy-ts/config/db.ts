import { Pool, PoolClient } from 'pg';
import { env } from './env';

let pool: Pool | null = null;

export function getPool(): Pool {
  if (!pool) {
    const connectionString = env.DATABASE_URL_DEV;
    if (!connectionString) {
      // Cho phép khởi tạo pool giả định khi chạy test mock
      pool = new Pool();
    } else {
      pool = new Pool({
        connectionString,
        ssl: connectionString.includes('supabase') ? { rejectUnauthorized: false } : false,
        max: 20,
        idleTimeoutMillis: 30000,
        connectionTimeoutMillis: 5000,
      });
    }

    pool.on('error', (err) => {
      console.error('Lỗi bất ngờ trên client PostgreSQL pool:', err);
    });
  }
  return pool;
}

export async function query(text: string, params?: any[]) {
  const p = getPool();
  return p.query(text, params);
}

export async function getClient(): Promise<PoolClient> {
  const p = getPool();
  return p.connect();
}
