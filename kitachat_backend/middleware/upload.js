const multer = require('multer');
const path = require('path');
const crypto = require('crypto');

const MAX_FILE_SIZE = 10 * 1024 * 1024;

const uploadDir =
  process.env.UPLOAD_DIR ||
  path.join(
    __dirname,
    '..',
    'data',
    'uploads'
  );

const storage = multer.diskStorage({
  destination: (
    req,
    file,
    callback
  ) => {
    callback(null, uploadDir);
  },

  filename: (
    req,
    file,
    callback
  ) => {
    const extension =
      path.extname(
        file.originalname
      ).toLowerCase();

    callback(
      null,
      `${crypto.randomUUID()}${extension}`
    );
  }
});

const imageUpload = multer({
  storage,
  limits: {
    fileSize: MAX_FILE_SIZE,
    files: 1
  },

  fileFilter: (
    req,
    file,
    callback
  ) => {
    if (
      !file.mimetype.startsWith(
        'image/'
      )
    ) {
      return callback(
        new Error(
          'Hanya file gambar yang diizinkan.'
        )
      );
    }

    callback(null, true);
  }
});

const mediaUpload = multer({
  storage,
  limits: {
    fileSize: MAX_FILE_SIZE,
    files: 1
  },

  fileFilter: (
    req,
    file,
    callback
  ) => {

    const isImage =
      file.mimetype.startsWith(
        'image/'
      );

    const isAudio =
      file.mimetype.startsWith(
        'audio/'
      ) ||
      file.mimetype ===
        'video/webm' ||
      file.mimetype ===
        'application/octet-stream';

    const isDocument = [
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      'text/plain',
      'application/zip'
    ].includes(file.mimetype);

    if (
      !isImage &&
      !isAudio &&
      !isDocument
    ) {
      return callback(
        new Error(
          'Hanya file gambar, audio, atau dokumen yang diizinkan.'
        )
      );
    }

    callback(null, true);
  }
});

module.exports = {
  imageUpload,
  mediaUpload,
  uploadDir,
  MAX_FILE_SIZE
};