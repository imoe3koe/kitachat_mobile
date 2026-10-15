const PUBLIC_BASE_URL =
  process.env.PUBLIC_BASE_URL ||
  'https://kitachat-app-production.up.railway.app';

function getUploadUrl(filename) {
  return `${PUBLIC_BASE_URL}/uploads/${filename}`;
}

module.exports = {
  getUploadUrl
};
