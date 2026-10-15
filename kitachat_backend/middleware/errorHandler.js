const multer = require('multer');

function errorHandler(
  error,
  req,
  res,
  next
) {
  if (res.headersSent) {
    return next(error);
  }

  if (
    error instanceof multer.MulterError &&
    error.code === 'LIMIT_FILE_SIZE'
  ) {
    return res.status(413).json({
      error: 'Ukuran file maksimal 10MB.'
    });
  }

  return res.status(400).json({
    error:
      error.message ||
      'Permintaan tidak valid.'
  });
}

module.exports = errorHandler;