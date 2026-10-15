const {
  addUser,
  removeUser,
  getSocketId,
  getOnlineUsers
} = require(
  '../services/userPresenceService'
);

function registerChatHandlers(
  socket,
  pool,
  activeUsers
) {
  addUser(
    socket.userId,
    socket.id
  );
  
  if (socket.familyCode) {
    socket.join(socket.familyCode);
    
    (async () => {
      try {
        const historyQuery = await pool.query(`
          SELECT 
            messages.id, 
            messages.user_id, 
            users.name, 
            messages.message, 
            messages.image_url, 
            messages.file_url,
            messages.audio_url, 
            messages.reply_to_id, 
            messages.is_read,
            messages.created_at, 
            messages.client_time 
          FROM messages 
          JOIN users ON messages.user_id = users.id 
          WHERE messages.family_code = $1 AND messages.is_deleted = FALSE 
          ORDER BY messages.created_at ASC 
          LIMIT 100
        `, [socket.familyCode]);

        console.log(
          '[SOCKET JOIN]',
          socket.userId,
          socket.familyCode
        );

        const formattedHistory = historyQuery.rows.map(row => ({
          id: row.id,
          user_id: row.user_id,
          name: row.name,
          message: row.message,
          message_type:
            row.image_url
              ? 'image'
              : row.file_url
                ? 'pdf'
                : row.audio_url
                  ? 'audio'
                  : 'text',
          image_url: row.image_url,
          file_url: row.file_url,
          audio_url: row.audio_url,
          reply_to_id: row.reply_to_id,
          is_read: row.is_read,
          time: row.client_time || new Date(row.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }));
        
        socket.emit('chat_history', formattedHistory);
      } catch (err) {
        console.error('Gagal memuat riwayat chat socket:', err);
      }
    })();
  }

  socket.server.to(socket.familyCode).emit('online_users_update', getOnlineUsers());

  socket.on('disconnect', () => {
    if (getSocketId(socket.userId) === socket.id) {
      removeUser(
        socket.userId,
        socket.id
      );
      if (socket.familyCode) {
        socket.server.to(socket.familyCode).emit('online_users_update', getOnlineUsers());
      }
    }
  });
}

module.exports = {
  registerChatHandlers
};