function registerReadReceiptHandlers(
  socket,
  pool
) {
  socket.on(
    'message_read',
    async data => {
      try {
        const messageId = Number(data.messageId);
        if (!messageId) return;

        const msg = await pool.query(
          `
          SELECT
            id,
            user_id,
            family_code,
            is_read
          FROM messages
          WHERE id = $1
        `,
          [messageId]
        );

        if (msg.rows.length === 0) return;

        const message = msg.rows[0];

        if (message.family_code !== socket.familyCode) {
          return;
        }

        if (Number(message.user_id) === Number(socket.userId)) {
          return;
        }

        if (message.is_read === true) {
          return;
        }

        await pool.query(
          `
          UPDATE messages
          SET
            is_read = TRUE,
            read_at = NOW()
          WHERE id = $1
        `,
          [messageId]
        );

        socket.server.to(socket.familyCode).emit('message_read_update', {
          id: String(messageId)
        });
      } catch (err) {
        console.error('message_read', err);
      }
    }
  );
}

module.exports = {
  registerReadReceiptHandlers
};