const express = require('express');
const router = express.Router();

const {
  register,
  login,
  forgotPassword,
  resetPassword
} = require('../controllers/authController');

router.post(
  '/api/register',
  register
);

router.post(
  '/api/login',
  login
);

router.post(
  '/api/forgot-password',
  forgotPassword
);

router.post(
  '/api/reset-password',
  resetPassword
);

module.exports = router;