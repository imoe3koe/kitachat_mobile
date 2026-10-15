function normalizePhone(phone) {
  return String(phone || '').replace(/[^0-9]/g, '');
}

module.exports = {
  normalizePhone
};