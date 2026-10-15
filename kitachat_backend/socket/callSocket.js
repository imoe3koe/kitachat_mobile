function registerCallHandlers(
  socket,
  pool,
  activeUsers
) {
  socket.on('call_user', async data => {
    const { toUserId, callId, offer, callerName } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) socket.server.to(targetSocketId).emit('incoming_call', { callId, fromUserId: socket.userId, callerName, offer });
    } catch (err) {
      console.error('Error call_user signaling:', err);
    }
  });

  socket.on('call_answer', async data => {
    const { toUserId, callId, answer } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) socket.server.to(targetSocketId).emit('call_answered', { callId, fromUserId: socket.userId, answer });
    } catch (err) {
      console.error('Error call_answer signaling:', err);
    }
  });

  socket.on('call_ice_candidate', async data => {
    const { toUserId, callId, candidate } = data;
    try {
      const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
      if (targetUser.rows.length === 0 || targetUser.rows[0].family_code !== socket.familyCode) return;
      const targetSocketId = activeUsers.get(String(toUserId));
      if (targetSocketId) socket.server.to(targetSocketId).emit('ice_candidate', { callId, fromUserId: socket.userId, candidate });
    } catch (err) {
      console.error('Error call_ice_candidate signaling:', err);
    }
  });

  socket.on('end_call', async data => {
    const { toUserId, callId } = data;
    try {
      if (toUserId) {
        const targetUser = await pool.query('SELECT family_code FROM users WHERE id = $1', [toUserId]);
        if (targetUser.rows.length > 0 && targetUser.rows[0].family_code === socket.familyCode) {
          const targetSocketId = activeUsers.get(String(toUserId));
          if (targetSocketId) socket.server.to(targetSocketId).emit('end_call', { callId, fromUserId: socket.userId });
        }
      } else if (socket.familyCode) {
        socket.to(socket.familyCode).emit('end_call', { callId, fromUserId: socket.userId });
      }
    } catch (err) {
      console.error('Error end_call signaling:', err);
    }
  });
}

module.exports = {
  registerCallHandlers
};