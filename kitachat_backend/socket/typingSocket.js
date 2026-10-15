function registerTypingHandlers(socket) {

  socket.on(
    'typing',
    () => {

      if (!socket.familyCode) {
        return;
      }

      socket.to(
        socket.familyCode
      ).emit(
        'user_typing',
        {
          userId:
            socket.userId
        }
      );
    }
  );

  socket.on(
    'stop_typing',
    () => {

      if (!socket.familyCode) {
        return;
      }

      socket.to(
        socket.familyCode
      ).emit(
        'user_stop_typing',
        {
          userId:
            socket.userId
        }
      );
    }
  );
}

module.exports = {
  registerTypingHandlers
};