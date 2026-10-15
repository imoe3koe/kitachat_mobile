const express = require('express');
const router = express.Router();

const checkSingleDevice =
  require('../middleware/auth');

const {
  getAgendas,
  createAgenda,
  deleteAgenda
} = require(
  '../controllers/agendaController'
);

router.get(
  '/api/agendas',
  checkSingleDevice,
  getAgendas
);

router.post(
  '/api/agendas',
  checkSingleDevice,
  createAgenda
);

router.delete(
  '/api/agendas/:id',
  checkSingleDevice,
  deleteAgenda
);

module.exports = router;