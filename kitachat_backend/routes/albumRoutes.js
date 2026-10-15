const express = require('express');
const router = express.Router();

const checkSingleDevice =
  require('../middleware/auth');

const {
  imageUpload
} = require('../middleware/upload');

const {
  getAlbums,
  uploadAlbum,
  deleteAlbum
} = require(
  '../controllers/albumController'
);

router.get(
  '/api/albums',
  checkSingleDevice,
  getAlbums
);

router.post(
  '/api/albums',
  checkSingleDevice,
  imageUpload.single('image'),
  uploadAlbum
);

router.delete(
  '/api/albums/:id',
  checkSingleDevice,
  deleteAlbum
);

module.exports = router;