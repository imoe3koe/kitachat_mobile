const pool = require('../config/database');

const {
  escapeHTML
} = require('../utils/sanitizer');

async function getAgendas(req, res) {
  try {

    const result = await pool.query(
      `
      SELECT
        id,
        title,
        event_date,
        description,
        created_at
      FROM agendas
      WHERE family_code = $1
      ORDER BY
        event_date ASC,
        created_at ASC
      `,
      [req.familyCode]
    );

    return res.json(
      result.rows
    );

  } catch (error) {

    console.error(
      'Error mengambil agenda:',
      error
    );

    return res.status(500).json({
      error:
        'Gagal memuat agenda.'
    });
  }
}

async function createAgenda(req, res) {
  let {
    title,
    event_date,
    description
  } = req.body;

  if (!title || !event_date) {
    return res.status(400).json({
      error:
        'Judul dan tanggal wajib diisi.'
    });
  }

  try {

    const result = await pool.query(
      `
      INSERT INTO agendas
      (
        family_code,
        title,
        event_date,
        description
      )
      VALUES
      (
        $1,
        $2,
        $3,
        $4
      )
      RETURNING
        id,
        title,
        event_date,
        description,
        created_at
      `,
      [
        req.familyCode,
        escapeHTML(
          title.trim()
        ),
        event_date,
        escapeHTML(
          String(
            description || ''
          ).trim()
        )
      ]
    );

    return res.status(201).json({
      message:
        'Agenda berhasil ditambahkan.',
      agenda:
        result.rows[0]
    });

  } catch (error) {

    console.error(
      'Error menambah agenda:',
      error
    );

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

async function deleteAgenda(req, res) {
  try {

    const result = await pool.query(
      `
      DELETE FROM agendas
      WHERE id = $1
      RETURNING id
      `,
      [req.params.id]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({
        error:
          'Agenda tidak ditemukan.'
      });
    }

    return res.json({
      message:
        'Agenda berhasil dihapus.'
    });

  } catch (error) {

    console.error(
      'Error menghapus agenda:',
      error
    );

    return res.status(500).json({
      error:
        'Terjadi kesalahan pada server.'
    });
  }
}

module.exports = {
  getAgendas,
  createAgenda,
  deleteAgenda
};