const activeUsers = new Map();

function addUser(
  userId,
  socketId
) {
  activeUsers.set(
    String(userId),
    socketId
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
    activeUsers.delete(
      String(userId)
    );
  }
}

function getSocketId(
  userId
) {

  return activeUsers.get(
    String(userId)
  );
}

function isOnline(
  userId
) {

  return activeUsers.has(
    String(userId)
  );
}

function getOnlineUsers() {

  return Array.from(
    activeUsers.keys()
  );
}

module.exports = {
  addUser,
  removeUser,
  getSocketId,
  isOnline,
  getOnlineUsers
};