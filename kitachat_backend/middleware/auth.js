const pool = require('../config/database');

async function checkSingleDevice(
  req,
  res,
  next
) {
  const userId = req.headers['x-user-id'];
  const clientToken =
    req.headers['x-session-token'];

  if (!userId || !clientToken) {
    return res.status(401).json({
      error:
        'Akses ditolak. Sesi tidak valid atau belum login.'
    });
  }

  try {
    const result = await pool.query(
      `
      SELECT
        id,
        session_token,
        family_code
      FROM users
      WHERE id = $1
      `,
      [userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        error: 'Pengguna tidak ditemukan.'
      });
    }

    const dbToken =
      result.rows[0].session_token;

    if (
      !dbToken ||
      dbToken !== clientToken
    ) {
      return res.status(403).json({
        error: 'SESSION_KICKED',
        message:
          'Akun Anda telah login di perangkat lain.'
      });
    }

    req.userId =
      result.rows[0].id;

    req.sessionToken =
      clientToken;

    req.familyCode =
      result.rows[0].family_code;

    next();
  } catch (error) {
    console.error(
      'Error checkSingleDevice:',
      error
    );

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

module.exports = checkSingleDevice;