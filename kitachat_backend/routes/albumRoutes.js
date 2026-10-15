const express = require('express');
const router = express.Router();

const pool = require('../config/database');

const checkSingleDevice =
  require('../middleware/auth');

const {
  imageUpload
} = require('../middleware/upload');

const {
  extractLocalUploadPath,
  deleteUploadedFile
} = require('../utils/file');

const {
  getUploadUrl
} = require('../utils/uploadUrl');




router.get('/api/albums', checkSingleDevice, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT albums.id, albums.user_id, albums.image_url, albums.caption, albums.created_at, users.name AS uploader_name
      FROM albums JOIN users ON albums.user_id = users.id WHERE users.family_code = $1 ORDER BY albums.created_at DESC
    `, [req.familyCode]);
    return res.json(result.rows);
  } catch (error) {
    console.error('Error mengambil album:', error);
    return res.status(500).json({ error: 'Gagal memuat album foto.' });
  }
});

router.post('/api/albums', checkSingleDevice, imageUpload.single('image'), async (req, res) => {
  const userId = req.userId;
  try {
    if (!req.file) return res.status(400).json({ error: 'File gambar wajib diunggah.' });
    const caption = String(req.body.caption || '').trim();
    const imageUrl = getUploadUrl(req.file.filename);

    const result = await pool.query('INSERT INTO albums (user_id, image_url, caption) VALUES ($1, $2, $3) RETURNING id, user_id, image_url, caption, created_at', [userId, imageUrl, escapeHTML(caption)]);
    return res.status(201).json({ message: 'Foto berhasil diunggah.', photo: result.rows[0] });
  } catch (error) {
    deleteUploadedFile(req.file);
    console.error('Error upload album:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
});

router.delete('/api/albums/:id', checkSingleDevice, async (req, res) => {
  const albumId = Number(req.params.id);
  const userId = req.userId;
  try {
    const result = await pool.query('DELETE FROM albums WHERE id = $1 AND user_id = $2 RETURNING image_url', [albumId, userId]);
    if (result.rowCount === 0) return res.status(403).json({ error: 'Anda tidak memiliki izin menghapus foto ini.' });
    if (result.rows[0].image_url) {
      deleteUploadedFile({
        path: extractLocalUploadPath(result.rows[0].image_url)
      });
    }
    return res.json({ message: 'Foto berhasil dihapus.' });
  } catch (error) {
    console.error('Error menghapus album:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
});

module.exports = router;
