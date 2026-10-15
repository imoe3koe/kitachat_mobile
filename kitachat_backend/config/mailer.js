const nodemailer = require('nodemailer');

const transporter = nodemailer.createTransport({
  host:
    process.env.EMAIL_HOST ||
    'smtp.gmail.com',

  port:
    Number(process.env.EMAIL_PORT) ||
    465,

  secure:
    process.env.EMAIL_SECURE !== 'false',

  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASS
  }
});

module.exports