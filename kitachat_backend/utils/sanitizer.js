function escapeHTML(value) {
  if (typeof value !== 'string') return value;

  return value.replace(/[&<>'"]/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;'
  })[character]);
}

module.exports = {
  escapeHTML
};