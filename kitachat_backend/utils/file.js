const path = require('path');
const fs = require('fs');

function extractLocalUploadPath(fileUrl, uploadDir) {
  if (!fileUrl) return null;

  try {
    const parsed = new URL(fileUrl);

    return path.join(
      uploadDir,
      parsed.pathname.replace(/^\/uploads\//, '')
    );
  } catch {
    return path.join(
      uploadDir,
      fileUrl.replace(/^\/uploads\//, '')
    );
  }
}

function deleteUploadedFile(file) {
  if (!file || !file.path) return;

  fs.unlink(file.path, error => {
    if (error && error.code !== 'ENOENT') {
      console.error('Gagal menghapus file upload:', error);
    }
  });
}

module.exports = {
  extractLocalUploadPath,
  deleteUploadedFile
};