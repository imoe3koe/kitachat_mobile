const express = require('express');
const router = express.Router();

const pool = require('../config/database');

const checkSingleDevice =
  require('../middleware/auth');

const {
  mediaUpload
} = require('../middleware/upload');

const {
  escapeHTML
} = require('../utils/sanitizer');

const {
  deleteUploadedFile
} = require('../utils/file');

const {
  getUploadUrl
} = require('../utils/uploadUrl');

const webpush = require('web-push');

const {
  getMessages
} = require(
  '../controllers/messageController'
);



router.get(
  '/api/messages',
  checkSingleDevice,
  getMessages
);


router.post('/api/send-message', checkSingleDevice, mediaUpload.single('media'), async (req, res) => {
  const userId = req.userId;
  const familyCode = req.familyCode;

  try {
    let { message, sticker_url, reply_to_id, client_time } = req.body;
    message = typeof message === 'string' ? escapeHTML(message.trim()) : '';

    let imageUrl = null;
    let audioUrl = null;
    let fileUrl = null;

    if (req.file) {
      const mimeType = req.file.mimetype;

      const isDocumentMime = [
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'text/plain',
        'application/zip'
      ].includes(mimeType);

      if (isDocumentMime) {
        fileUrl = getUploadUrl(req.file.filename);
      } else if (
        mimeType.startsWith('audio/') ||
        mimeType === 'video/webm'
      ) {
        audioUrl = getUploadUrl(req.file.filename);
      } else {
        imageUrl = getUploadUrl(req.file.filename);
      }
    }

    const stickerUrl = sticker_url ? String(sticker_url).trim() : null;
    const replyToId = reply_to_id ? Number.parseInt(reply_to_id, 10) : null;

    if (!message && !imageUrl && !audioUrl && !fileUrl && !stickerUrl) {
      deleteUploadedFile(req.file);
      return res.status(400).json({ error: 'Pesan tidak boleh kosong.' });
    }

    const result = await pool.query(
      `INSERT INTO messages (family_code, user_id, message, image_url, file_url, sticker_url, audio_url, reply_to_id, client_time)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
       RETURNING id, family_code, user_id, message, image_url, file_url, sticker_url, audio_url, reply_to_id, is_deleted, is_read, created_at, client_time`,
      [familyCode, userId, message, imageUrl, fileUrl, stickerUrl, audioUrl, replyToId, client_time || null]
    );

    const savedMessage = result.rows[0];
    const userResult = await pool.query('SELECT name FROM users WHERE id = $1', [userId]);

    let replyText = null;
    if (savedMessage.reply_to_id) {
      const parentMsgQuery = await pool.query('SELECT message FROM messages WHERE id = $1', [savedMessage.reply_to_id]);
      if (parentMsgQuery.rows.length > 0) {
        replyText = parentMsgQuery.rows[0].message || '(Lampiran Media)';
      }
    }

    const messagePayload = {
      id: savedMessage.id,
      user_id: savedMessage.user_id,
      name: userResult.rows[0]?.name || 'Keluarga',
      message: savedMessage.message,
      message_type:
        savedMessage.image_url
          ? 'image'
          : savedMessage.file_url
            ? 'pdf'
            : savedMessage.audio_url
              ? 'audio'
              : 'text',
      image_url: savedMessage.image_url,
      file_url: savedMessage.file_url,
      audio_url: savedMessage.audio_url,
      reply_to_id: savedMessage.reply_to_id,
      reply_text: replyText,
      is_read: savedMessage.is_read,
      time: savedMessage.client_time || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    console.log(
      '[REALTIME]',
      familyCode,
      savedMessage.id,
      savedMessage.user_id
    );

    io.to(familyCode).emit('receive_message', messagePayload);

    try {
      const subs = await pool.query(`
        SELECT push_subscriptions.* FROM push_subscriptions
        JOIN users ON push_subscriptions.user_id = users.id
        WHERE push_subscriptions.user_id != $1 AND users.family_code = $2
      `, [userId, familyCode]);

      const pushPayload = JSON.stringify({
        title: messagePayload.name,
        body: savedMessage.message || 'Mengirim lampiran media',
        url: '/'
      });

      if (process.env.VAPID_PUBLIC_KEY && process.env.VAPID_PRIVATE_KEY) {
        subs.rows.forEach(sub => {
          const pushSub = { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } };
          webpush.sendNotification(pushSub, pushPayload).catch(err => {
            if (err.statusCode === 410 || err.statusCode === 404) {
              pool.query('DELETE FROM push_subscriptions WHERE endpoint = $1', [sub.endpoint]).catch(() => {});
            } else {
              console.error('Push error:', err);
            }
          });
        });
      }
    } catch (pushErr) {
      console.error('Gagal mengirim push notification:', pushErr);
    }

    return res.status(201).json({ message: 'Pesan dikirim.', data: messagePayload });
  } catch (error) {
    deleteUploadedFile(req.file);
    console.error('Error mengirim pesan:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
});


router.delete('/api/messages/:id', checkSingleDevice, async (req, res) => {
  try {
    const result = await pool.query(
      `UPDATE messages SET is_deleted = TRUE, message = 'Pesan telah dihapus', image_url = NULL, file_url = NULL, sticker_url = NULL, audio_url = NULL WHERE id = $1 AND user_id = $2 RETURNING id`,
      [Number(req.params.id), req.userId]
    );
    if (result.rowCount === 0) return res.status(403).json({ error: 'Tidak memiliki hak.' });
    io.to(req.familyCode).emit('message_deleted', { id: Number(req.params.id) });
    return res.json({ message: 'Pesan dihapus.' });
  } catch (error) {
    console.error('Error hapus pesan:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
});

router.delete('/api/messages', checkSingleDevice, async (req, res) => {
  try {
    await pool.query('DELETE FROM messages WHERE user_id = $1', [req.userId]);
    io.to(req.familyCode).emit('messages_deleted_by_user', { user_id: req.userId });
    return res.json({ message: 'Riwayat pesan dibersihkan.' });
  } catch (error) {
    console.error('Error clear messages:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
});


module.exports = router;