const express = require('express');
const router = express.Router();

const pool = require('../config/database');

const checkSingleDevice =
  require('../middleware/auth');

const {
  imageUpload
} = require('../middleware/upload');

const {
  isOnline
} = require(
  '../services/userPresenceService'
);

const {
  getFamilyBirthdays,
  updatePhoto,
  updateProfile,
  updateEmail,
  updatePassword
} = require('../controllers/userController');

router.get(
  '/api/users',
  checkSingleDevice,
  async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT
           id,
           name,
           phone,
           birthdate,
           photo_url,
           created_at
         FROM users
         WHERE family_code = $1
         ORDER BY name ASC`,
        [req.familyCode]
      );

      const usersWithMaskedPhone =
        result.rows.map(user => {

          let phone =
            user.phone || '';

          if (
            phone.length > 6 &&
            Number(req.userId) !==
            Number(user.id)
          ) {
            phone =
              phone.substring(0, 4) +
              '****' +
              phone.substring(
                phone.length - 3
              );
          }

          return {
            ...user,
            phone,
            is_online: isOnline(user.id)
          };
        });

      return res.json(
        usersWithMaskedPhone
      );

    } catch (error) {

      console.error(
        'Error mengambil users:',
        error
      );

      return res.status(500).json({
        error:
          'Gagal memuat daftar keluarga.'
      });
    }
  }
);

router.get(
  '/api/family-birthdays',
  checkSingleDevice,
  getFamilyBirthdays
);

router.post(
  '/api/update-photo',
  checkSingleDevice,
  imageUpload.single('image'),
  updatePhoto
);

router.put(
  '/api/update-profile',
  checkSingleDevice,
  updateProfile
);

router.put(
  '/api/update-email',
  checkSingleDevice,
  updateEmail
);

router.put(
  '/api/update-password',
  checkSingleDevice,
  updatePassword
);

module.exports = router;