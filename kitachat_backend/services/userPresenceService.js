const activeUsers = new Map();

function addUser(
  userId,
  socketId
) {
  addUser(
  socket.userId,
  socket.id
  );
}

function removeUser(
  userId,
  socketId
) {

  const current =
    activeUsers.get(
      String(userId)
    );

  if (
    current === socketId
  ) {
    removeUser(
  socket.userId,
  socket.id
  );
  }
}

function getSocketId(
  userId
) {

  getSocketId(
  toUserId
);
}

function isOnline(
  userId
) {

  isOnline(
  user.id
);
}

function getOnlineUsers() {

  getOnlineUsers();
}

module.exports = {
  addUser,
  removeUser,
  getSocketId,
  isOnline,
  getOnlineUsers
};