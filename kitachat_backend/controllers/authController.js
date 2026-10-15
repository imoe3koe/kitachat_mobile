const pool = require('../config/database');
const transporter = require('../config/mailer');

const bcrypt = require('bcrypt');
const crypto = require('crypto');

const {
  normalizePhone
} = require('../utils/phone');

const {
  escapeHTML
} = require('../utils/sanitizer');

const {
  isValidPassword
} = require('../utils/validation');


async function register(req, res) {
  let { phone, name, password, birthdate, photo_url, register_type, invite_code } = req.body;

  if (!phone || !name || !password || !register_type) {
    return res.status(400).json({
      error: 'Nomor telepon, nama, password, dan jenis pendaftaran wajib diisi.'
    });
  }

  phone = normalizePhone(phone);
  name = String(name).trim();

  try {
    const existingUser = await pool.query('SELECT id FROM users WHERE phone = $1', [phone]);

    if (existingUser.rows.length > 0) {
      return res.status(409).json({ error: 'Nomor telepon sudah terdaftar di Kitachat.' });
    }

    let finalFamilyCode = '';
    let isHead = false;

    if (register_type === 'create') {
      finalFamilyCode = 'KITA-' + crypto.randomBytes(3).toString('hex').toUpperCase();
      isHead = true;
    } else if (register_type === 'join') {
      if (!invite_code) {
        return res.status(400).json({ error: 'Kode undangan keluarga wajib diisi.' });
      }

      const checkCode = await pool.query('SELECT family_code FROM users WHERE family_code = $1 LIMIT 1', [invite_code.trim().toUpperCase()]);
      if (checkCode.rows.length === 0) {
        return res.status(404).json({ error: 'Kode undangan keluarga tidak valid atau tidak ditemukan.' });
      }

      finalFamilyCode = checkCode.rows[0].family_code;
      isHead = false;
    } else {
      return res.status(400).json({ error: 'Tipe pendaftaran tidak valid.' });
    }

    const hashedPassword = await bcrypt.hash(password, 12);
    const sessionToken = crypto.randomBytes(32).toString('hex');

    const result = await pool.query(
      `
        INSERT INTO users (phone, name, password, birthdate, photo_url, session_token, family_code, is_head)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        RETURNING id, phone, name, birthdate, photo_url, family_code, is_head, created_at
      `,
      [phone, escapeHTML(name), hashedPassword, birthdate || null, photo_url || null, sessionToken, finalFamilyCode, isHead]
    );

    return res.status(201).json({
      message: isHead
        ? `Keluarga berhasil dibuat! Kode Undangan Anda: ${finalFamilyCode}`
        : 'Berhasil bergabung ke dalam keluarga!',
      session_token: sessionToken,
      family_code: finalFamilyCode,
      user: result.rows[0]
    });
  } catch (error) {
    console.error('Error register:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
}

async function login(req, res) {
  let { phone, password } = req.body;

  if (!phone || !password) {
    return res.status(400).json({ error: 'Nomor telepon dan password wajib diisi.' });
  }

  phone = normalizePhone(phone);

  try {
    const result = await pool.query('SELECT id, phone, name, password, birthdate, photo_url, family_code FROM users WHERE phone = $1', [phone]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Nomor telepon belum terdaftar.' });

    const user = result.rows[0];
    const passwordValid = await bcrypt.compare(password, user.password);
    if (!passwordValid) return res.status(401).json({ error: 'Password salah.' });

    const sessionToken = crypto.randomBytes(32).toString('hex');
    await pool.query('UPDATE users SET session_token = $1 WHERE id = $2', [sessionToken, user.id]);

    return res.json({
      message: 'Login berhasil!',
      session_token: sessionToken,
      user: {
        id: user.id,
        phone: user.phone,
        name: user.name,
        birthdate: user.birthdate,
        photo_url: user.photo_url,
        family_code: user.family_code
      }
    });
  } catch (error) {
    console.error('Error login:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
}

async function forgotPassword(req, res) {
  let { phone } = req.body;
  if (!phone) return res.status(400).json({ error: 'Nomor telepon wajib diisi.' });
  phone = normalizePhone(phone);
  try {
    const userResult = await pool.query('SELECT id, email FROM users WHERE phone = $1', [phone]);
    if (userResult.rows.length === 0) return res.status(404).json({ error: 'Nomor telepon belum terdaftar.' });
    const user = userResult.rows[0];
    if (!user.email) return res.status(400).json({ error: 'Anda belum mengatur email pemulihan di menu pengaturan.' });

    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiry = Date.now() + 15 * 60 * 1000;
    await pool.query('UPDATE users SET reset_token = $1, reset_token_expiry = $2 WHERE id = $3', [otp, expiry, user.id]);

    await transporter.sendMail({
      from: process.env.EMAIL_USER,
      to: user.email,
      subject: 'Kode OTP Pemulihan Password Kitachat',
      text: `Halo, kode OTP pemulihan akun Kitachat Anda adalah: ${otp}. Kode ini berlaku selama 15 menit.`
    });
    return res.json({ message: 'Kode OTP 6 digit telah dikirim ke email pemulihan Anda.' });
  } catch (error) {
    console.error('Error forgot-password:', error);
    return res.status(500).json({ error: 'Gagal mengirim email OTP. Periksa konfigurasi email server.' });
  }
}

async function resetPassword(req, res) {
  let { phone, otp, new_password } = req.body;
  if (!phone || !otp || !new_password) return res.status(400).json({ error: 'Nomor telepon, OTP, dan password baru wajib diisi.' });
  if (!isValidPassword(new_password)) return res.status(400).json({ error: 'Password baru minimal 8 karakter.' });
  phone = normalizePhone(phone);
  try {
    const userResult = await pool.query('SELECT id, reset_token, reset_token_expiry FROM users WHERE phone = $1', [phone]);
    if (userResult.rows.length === 0) return res.status(404).json({ error: 'Pengguna tidak ditemukan.' });
    const user = userResult.rows[0];

    if (!user.reset_token || user.reset_token !== otp || Date.now() > Number(user.reset_token_expiry)) {
      return res.status(400).json({ error: 'Kode OTP salah atau sudah kedaluwarsa.' });
    }

    const hashedPassword = await bcrypt.hash(new_password, 12);
    await pool.query('UPDATE users SET password = $1, reset_token = NULL, reset_token_expiry = NULL WHERE id = $2', [hashedPassword, user.id]);
    return res.json({ message: 'Password berhasil diubah. Silakan masuk dengan password baru Anda.' });
  } catch (error) {
    console.error('Error reset-password:', error);
    return res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
  }
}


module.exports = {
  register,
  login,
  forgotPassword,
  resetPassword
};