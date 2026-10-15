const pool = require('../config/database');


async function initDB() {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        phone VARCHAR(20) UNIQUE NOT NULL,
        name VARCHAR(100) NOT NULL,
        password VARCHAR(255) NOT NULL,
        birthdate DATE,
        photo_url TEXT,
        session_token TEXT,
        email VARCHAR(255) UNIQUE,
        family_code VARCHAR(50),
        is_head BOOLEAN DEFAULT FALSE,
        reset_token VARCHAR(255),
        reset_token_expiry BIGINT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS albums (
        id SERIAL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        image_url TEXT NOT NULL,
        caption TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS agendas (
        id SERIAL PRIMARY KEY,
        family_code VARCHAR(50),
        title VARCHAR(150) NOT NULL,
        event_date DATE NOT NULL,
        description TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        family_code VARCHAR(50),
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        message TEXT,
        image_url TEXT,
        file_url TEXT,
        sticker_url TEXT,
        audio_url TEXT,
        reply_to_id INT REFERENCES messages(id) ON DELETE SET NULL,
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        is_read BOOLEAN DEFAULT FALSE,
        read_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        client_time VARCHAR(50)
      );

      CREATE TABLE IF NOT EXISTS push_subscriptions (
        id SERIAL PRIMARY KEY,
        user_id INT REFERENCES users(id) ON DELETE CASCADE,
        endpoint TEXT NOT NULL UNIQUE,
        p256dh TEXT NOT NULL,
        auth TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
      );
    `);

    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255)');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS family_code VARCHAR(50)');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS is_head BOOLEAN DEFAULT FALSE');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token VARCHAR(255)');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token_expiry BIGINT');
    await client.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS family_code VARCHAR(50)');
    await client.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_url TEXT');
    await client.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_id INT');
    await client.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT FALSE');
    await client.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ');
    await client.query('ALTER TABLE agendas ADD COLUMN IF NOT EXISTS family_code VARCHAR(50)');

    await client.query('CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_messages_user_id ON messages(user_id)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_messages_family_code ON messages(family_code)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_messages_reply_to_id ON messages(reply_to_id)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_albums_created_at ON albums(created_at)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_albums_user_id ON albums(user_id)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_users_session_token ON users(session_token)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_users_family_code ON users(family_code)');
    await client.query('CREATE INDEX IF NOT EXISTS idx_agendas_event_date ON agendas(event_date)');

    await client.query('COMMIT');
    console.log('Database berhasil diinisialisasi dan diselaraskan.');
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('Gagal menginisialisasi database:', error.message);
    throw error;
  } finally {
    client.release();
  }
}
module.exports = initDB;