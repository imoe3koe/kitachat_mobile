const pool = require('../config/database');

async function getMessages(req, res) {
  try {

    const historyQuery = await pool.query(
      `
      SELECT
        messages.id,
        messages.family_code,
        messages.user_id,
        users.name AS sender,
        messages.message AS text,
        messages.image_url,
        messages.file_url,
        messages.audio_url,
        messages.reply_to_id,
        messages.is_deleted,
        messages.is_read,
        messages.created_at AS timestamp,
        messages.client_time
      FROM messages
      JOIN users
        ON messages.user_id = users.id
      WHERE messages.family_code = $1
      ORDER BY messages.created_at ASC
      LIMIT 100
      `,
      [req.familyCode]
    );

    const formattedMessages =
      historyQuery.rows.map(row => ({
        id: String(row.id),
        family_code: row.family_code,
        user_id: row.user_id,
        sender: row.sender,
        text:
          row.is_deleted
            ? 'Pesan ini telah dihapus'
            : row.text,
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
        reply_to_id:
          row.reply_to_id
            ? String(row.reply_to_id)
            : null,
        is_deleted: row.is_deleted,
        is_read: row.is_read,
        timestamp:
          row.client_time ||
          new Date(
            row.created_at
          ).toLocaleTimeString(
            [],
            {
              hour: '2-digit',
              minute: '2-digit'
            }
          )
      }));

    return res.json(
      formattedMessages
    );

  } catch (error) {

    console.error(
      'Error GET /api/messages:',
      error
    );

    return res.status(500).json({
      error:
        'Gagal memuat riwayat pesan.'
    });
  }
}

module.exports = {
  getMessages
};