const pool = require('../config/database');
const bcrypt = require('bcrypt');

const {
  escapeHTML
} = require('../utils/sanitizer');

const {
  isValidEmail,
  isValidPassword
} = require('../utils/validation');

const {
  extractLocalUploadPath,
  deleteUploadedFile
} = require('../utils/file');

const {
  getUploadUrl
} = require('../utils/uploadUrl');

async function getFamilyBirthdays(req, res) {
  try {
    const result = await pool.query(
      `SELECT
         id,
         name,
         photo_url AS profile_picture,
         birthdate AS birth_date
       FROM users
       WHERE family_code = $1
       ORDER BY birthdate ASC NULLS LAST, name ASC`,
      [req.familyCode]
    );

    res.json(result.rows);
  } catch (err) {
    console.error(
      'Error fetching birthdays:',
      err
    );

    res.status(500).json({
      error:
        'Gagal memuat data ulang tahun.'
    });
  }
}

async function updatePhoto(req, res) {
  const userId = req.userId;

  try {
    if (!req.file) {
      return res.status(400).json({
        error: 'File foto wajib diunggah.'
      });
    }

    const oldUser = await pool.query(
      'SELECT photo_url FROM users WHERE id = $1',
      [userId]
    );

    const photoUrl =
      getUploadUrl(req.file.filename);

    const result = await pool.query(
      `UPDATE users
       SET photo_url = $1
       WHERE id = $2
       RETURNING
         id,
         phone,
         name,
         birthdate,
         photo_url,
         family_code`,
      [photoUrl, userId]
    );

    if (oldUser.rows[0]?.photo_url) {
      deleteUploadedFile({
        path: extractLocalUploadPath(
          oldUser.rows[0].photo_url
        )
      });
    }

    return res.json({
      message: 'Foto profil diperbarui.',
      user: result.rows[0]
    });

  } catch (error) {

    deleteUploadedFile(req.file);

    console.error(
      'Error update foto:',
      error
    );

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

async function updateProfile(req, res) {
  const userId = req.userId;

  let {
    name,
    birthdate
  } = req.body;

  if (!name) {
    return res.status(400).json({
      error: 'Nama profil wajib diisi.'
    });
  }

  try {
    const result = await pool.query(
      `UPDATE users
       SET name = $1,
           birthdate = $2
       WHERE id = $3
       RETURNING
         id,
         phone,
         name,
         birthdate,
         photo_url,
         family_code`,
      [
        escapeHTML(name.trim()),
        birthdate || null,
        userId
      ]
    );

    return res.json({
      message: 'Profil diperbarui.',
      user: result.rows[0]
    });

  } catch (error) {

    console.error(
      'Error update profile:',
      error
    );

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

async function updateEmail(req, res) {
  const email = String(
    req.body.email || ''
  )
    .trim()
    .toLowerCase();

  if (!isValidEmail(email)) {
    return res.status(400).json({
      error: 'Email tidak valid.'
    });
  }

  try {

    await pool.query(
      'UPDATE users SET email = $1 WHERE id = $2',
      [email, req.userId]
    );

    return res.json({
      message: 'Email disimpan.'
    });

  } catch (error) {

    if (error.code === '23505') {
      return res.status(409).json({
        error: 'Email sudah digunakan.'
      });
    }

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

async function updatePassword(req, res) {
  const {
    old_password,
    new_password
  } = req.body;

  if (!isValidPassword(new_password)) {
    return res.status(400).json({
      error:
        'Password minimal 8 karakter.'
    });
  }

  try {

    const user = await pool.query(
      'SELECT password FROM users WHERE id = $1',
      [req.userId]
    );

    if (
      user.rows.length === 0 ||
      !(await bcrypt.compare(
        old_password,
        user.rows[0].password
      ))
    ) {
      return res.status(401).json({
        error: 'Password lama salah.'
      });
    }

    const hashed = await bcrypt.hash(
      new_password,
      12
    );

    await pool.query(
      'UPDATE users SET password = $1 WHERE id = $2',
      [hashed, req.userId]
    );

    return res.json({
      message:
        'Password diperbarui.'
    });

  } catch (error) {

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

module.exports = {
  getFamilyBirthdays,
  updatePhoto,
  updateProfile,
  updateEmail,
  updatePassword
};