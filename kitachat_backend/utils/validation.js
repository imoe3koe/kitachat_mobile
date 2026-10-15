const PASSWORD_MIN_LENGTH = 8;

function isValidPassword(password) {
  return (
    typeof password === 'string' &&
    password.length >= PASSWORD_MIN_LENGTH
  );
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

module.exports = {
  isValidPassword,
  isValidEmail,
  PASSWORD_MIN_LENGTH
};