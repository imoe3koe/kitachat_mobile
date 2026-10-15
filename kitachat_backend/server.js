require('dotenv').config();

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const authRoutes = require('./routes/authRoutes');

const bcrypt = require('bcrypt');
const multer = require('multer');
const path = require('path');
const nodemailer = require('nodemailer');
const fs = require('fs');
const crypto = require('crypto');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const cors = require('cors');
const webpush = require('web-push');

const userRoutes =
  require('./routes/userRoutes');

const agendaRoutes =
  require('./routes/agendaRoutes');

const initDB =
  require('./database/initDb');

const pool =
  require('./config/database');

const transporter =
  require('./config/mailer');

const {
  normalizePhone
} = require('./utils/phone');

const {
  escapeHTML
} = require('./utils/sanitizer');

const {
  isValidEmail,
  isValidPassword
} = require('./utils/validation');

const {
  extractLocalUploadPath,
  deleteUploadedFile
} = require('./utils/file');

const checkSingleDevice =
  require('./middleware/auth');

const {
  setIO
} = require('./services/realtimeService');

const app = express();
app.set('trust proxy', 1);
const server = http.createServer(app);

const {
  activeUsers
} = require('./services/userPresenceService');

const {
  registerTypingHandlers
} = require(
  './socket/typingSocket'
);

const {
  registerReadReceiptHandlers
} = require(
  './socket/readReceiptSocket'
);

const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(origin => origin.trim())
  : '*';

const io = new Server(server, {
  cors: {
    origin: allowedOrigins,
    methods: ['GET', 'POST'],
    credentials: true
  }
});

setIO(io);

const PORT = Number(process.env.PORT) || 3000;
const uploadDir = process.env.UPLOAD_DIR || path.join(__dirname, 'data', 'uploads');

const MAX_FILE_SIZE = 10 * 1024 * 1024;
const PASSWORD_MIN_LENGTH = 8;

if (process.env.VAPID_PUBLIC_KEY && process.env.VAPID_PRIVATE_KEY) {
  webpush.setVapidDetails(
    process.env.EMAIL_CONTACT || 'mailto:admin@kitachat.com',
    process.env.VAPID_PUBLIC_KEY,
    process.env.VAPID_PRIVATE_KEY
  );
}

function ensureUploadDirectory() {
  try {
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
      console.log(`Folder upload berhasil dibuat: ${uploadDir}`);
    }
  } catch (error) {
    console.error('Gagal membuat folder upload:', error);
    throw error;
  }
}

ensureUploadDirectory();

app.disable('x-powered-by');

app.use(cors({
  origin: allowedOrigins,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: [
    'Content-Type',
    'Authorization',
    'x-user-id',
    'x-session-token'
  ],
  credentials: true
}));

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  next();
});

app.use((req, res, next) => {
  const requestPath = req.path;
  if (requestPath === '/sw.js' || requestPath === '/app.js') {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
  }
  next();
});

app.use(express.static(path.join(__dirname, 'public')));

app.use(
  '/uploads',
  express.static(uploadDir, {
    dotfiles: 'deny',
    index: false,
    maxAge: process.env.NODE_ENV === 'production' ? '1d' : 0
  })
);

app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: [
          "'self'",
          "'unsafe-inline'",
          "https://jsdelivr.net",
          "https://cloudflare.com"
        ],
        styleSrc: [
          "'self'",
          "'unsafe-inline'",
          "https://cloudflare.com"
        ],
        imgSrc: ["'self'", "data:", "blob:", "https:"],
        fontSrc: [
          "'self'",
          "https://cloudflare.com",
          "data:"
        ],
      },
    },
  })
);

const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 150,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Terlalu banyak permintaan dari IP ini, silakan coba lagi setelah 15 menit.' }
});

app.use('/api/', apiLimiter);
app.use(authRoutes);
app.use(userRoutes);

const albumRoutes =
  require('./routes/albumRoutes');

app.use(albumRoutes);
app.use(agendaRoutes);

const messageRoutes =
  require('./routes/messageRoutes');

app.use(messageRoutes);

const storage = multer.diskStorage({
  destination: (req, file, callback) => {
    callback(null, uploadDir);
  },
  filename: (req, file, callback) => {
    const extension = path.extname(file.originalname).toLowerCase();
    callback(null, `${crypto.randomUUID()}${extension}`);
  }
});

const imageUpload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE, files: 1 },
  fileFilter: (req, file, callback) => {
    if (!file.mimetype.startsWith('image/')) {
      return callback(new Error('Hanya file gambar yang diizinkan.'));
    }
    callback(null, true);
  }
});

const mediaUpload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE, files: 1 },
  fileFilter: (req, file, callback) => {
    const isImage = file.mimetype.startsWith('image/');
    const isAudio =
      file.mimetype.startsWith('audio/') ||
      file.mimetype === 'video/webm' ||
      file.mimetype === 'application/octet-stream';
      
    const isDocument = [
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      'text/plain',
      'application/zip'
    ].includes(file.mimetype);

    if (!isImage && !isAudio && !isDocument) {
      return callback(new Error('Hanya file gambar, audio, atau dokumen yang diizinkan.'));
    }
    callback(null, true);
  }
});

const PUBLIC_BASE_URL =
  process.env.PUBLIC_BASE_URL ||
  'https://kitachat-app-production.up.railway.app';

function getUploadUrl(filename) {
  return `${PUBLIC_BASE_URL}/uploads/${filename}`;
}

app.post('/api/save-subscription', checkSingleDevice, async (req, res) => {
  const userId = req.userId;
  const { endpoint, keys } = req.body;

  if (!endpoint || !keys) {
    return res.status(400).json({ error: 'Data langganan tidak valid.' });
  }

  try {
    await pool.query(
      `INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (endpoint) DO NOTHING`,
      [userId, endpoint, keys.p256dh, keys.auth]
    );
    res.json({ message: 'Langganan push notification berhasil disimpan.' });
  } catch (err) {
    console.error('Error save subscription:', err);
    res.status(500).json({ error: 'Gagal menyimpan langganan push.' });
  }
});

io.use(async (socket, next) => {
  const auth = socket.handshake.auth || {};
  if (!auth.userId || !auth.sessionToken) return next(new Error('Unauthorized'));
  try {
    const res = await pool.query('SELECT id, family_code FROM users WHERE id = $1 AND session_token = $2', [auth.userId, auth.sessionToken]);
    if (res.rows.length === 0) return next(new Error('Unauthorized'));
    socket.userId = String(auth.userId);
    socket.familyCode = res.rows[0].family_code;
    next();
  } catch (e) {
    next(new Error('Authentication failed'));
  }
});

io.on('connection', async socket => {
  activeUsers.set(socket.userId, socket.id);
  if (socket.familyCode) {
    socket.join(socket.familyCode);
    try {
      const historyQuery = await pool.query(`
        SELECT 
          messages.id, 
          messages.user_id, 
          users.name, 
          messages.message, 
          messages.image_url, 
          messages.file_url,
          messages.audio_url, 
          messages.reply_to_id, 
          messages.is_read,
          messages.created_at, 
          messages.client_time 
        FROM messages 
        JOIN users ON messages.user_id = users.id 
        WHERE messages.family_code = $1 AND messages.is_deleted = FALSE 
        ORDER BY messages.created_at ASC 
        LIMIT 100
      `, [socket.familyCode]);

      console.log(
        '[SOCKET JOIN]',
        socket.userId,
        socket.familyCode
      );

      const formattedHistory = historyQuery.rows.map(row => ({
        id: row.id,
        user_id: row.user_id,
        name: row.name,
        message: row.message,
        message_type:
          row.image_url
            ? 'image'
            : row.file_url
              ? 'pdf'
              : row.audio_url
                ? 'audio'
                : 'text',
        image_url: row.image_url,
        file_url: row.file_url,
        audio_url: row.audio_url,
        reply_to_id: row.reply_to_id,
        is_read: row.is_read,
        time: row.client_time || new Date(row.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }));
      socket.emit('chat_history', formattedHistory);
    } catch (err) {
      console.error('Gagal memuat riwayat chat socket:', err);
    }
  }

  io.to(socket.familyCode).emit('online_users_update', Array.from(activeUsers.keys()));

  // Mendaftarkan handler typing modular
  registerTypingHandlers(socket);

  // Mendaftarkan handler read receipt modular
  registerReadReceiptHandlers(
    socket,
    pool
  );

  socket.on('call_user', async data => {
    const { toUserId, callId, offer, callerName } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) io.to(targetSocketId).emit('incoming_call', { callId, fromUserId: socket.userId, callerName, offer });
    } catch (err) {
      console.error('Error call_user signaling:', err);
    }
  });

  socket.on('call_answer', async data => {
    const { toUserId, callId, answer } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) io.to(targetSocketId).emit('call_answered', { callId, fromUserId: socket.userId, answer });
    } catch (err) {
      console.error('Error call_answer signaling:', err);
    }
  });

  socket.on('call_ice_candidate', async data => {
    const { toUserId, callId, candidate } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) io.to(targetSocketId).emit('ice_candidate', { callId, fromUserId: socket.userId, candidate });
    } catch (err) {
      console.error('Error call_ice_candidate signaling:', err);
    }
  });

  socket.on('end_call', async data => {
    const { toUserId, callId } = data;
    try {
      if (toUserId) {
        const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
        if (targetUser.rows.length > 0 && targetUser.rows[0].family_code === socket.familyCode) {
          const targetSocketId = activeUsers.get(String(toUserId));
          if (targetSocketId) io.to(targetSocketId).emit('end_call', { callId, fromUserId: socket.userId });
        }
      } else if (socket.familyCode) {
        socket.to(socket.familyCode).emit('end_call', { callId, fromUserId: socket.userId });
      }
    } catch (err) {
      console.error('Error end_call signaling:', err);
    }
  });

  socket.on('disconnect', () => {
    if (activeUsers.get(socket.userId) === socket.id) {
      activeUsers.delete(socket.userId);
      if (socket.familyCode) io.to(socket.familyCode).emit('online_users_update', Array.from(activeUsers.keys()));
    }
  });
});

app.use((error, req, res, next) => {
  if (res.headersSent) return next(error);
  if (error instanceof multer.MulterError && error.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: 'Ukuran file maksimal 10MB.' });
  }
  return res.status(400).json({ error: error.message || 'Permintaan tidak valid.' });
});

async function startServer() {
  try {
    await initDB();
    server.listen(PORT, () => {
      console.log(`Server Kitachat aktif di port ${PORT}.`);
    });
  } catch (error) {
    console.error('Server gagal dijalankan:', error);
    await pool.end();
    process.exit(1);
  }
}

startServer();