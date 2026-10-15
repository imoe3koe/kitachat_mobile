const { Pool } = require('pg');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: Math.max(
    1,
    Number(process.env.DB_POOL_MAX) || 5
  ),
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000,
  family: 4,
  ssl: {
    rejectUnauthorized: false
  }
});

pool.on('error', error => {
  console.error(
    'Unexpected PostgreSQL pool error:',
    error
  );
});

module.exports = pool;