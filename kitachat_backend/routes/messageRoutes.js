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
  getMessages,
  deleteMessage,
  deleteMessages,
  sendMessage
} = require(
  '../controllers/messageController'
);

const {
  sendPushNotification
} = require('../services/pushService');

router.get(
  '/api/messages',
  checkSingleDevice,
  getMessages
);

router.post(
  '/api/send-message',
  checkSingleDevice,
  mediaUpload.single('media'),
  sendMessage
);

router.delete(
  '/api/messages/:id',
  checkSingleDevice,
  deleteMessage
);

router.delete(
  '/api/messages',
  checkSingleDevice,
  deleteMessages
);

module.exports = router;