const webpush = require('web-push');

async function sendPushNotification(
  subscriptions,
  payload,
  pool
) {

  for (const sub of subscriptions) {

    const pushSub = {
      endpoint: sub.endpoint,
      keys: {
        p256dh: sub.p256dh,
        auth: sub.auth
      }
    };

    try {

      await webpush.sendNotification(
        pushSub,
        payload
      );

    } catch (err) {

      if (
        err.statusCode === 410 ||
        err.statusCode === 404
      ) {

        pool.query(
          `
          DELETE FROM push_subscriptions
          WHERE endpoint = $1
          `,
          [sub.endpoint]
        ).catch(() => {});

      } else {

        console.error(
          'Push error:',
          err
        );
      }
    }
  }
}

module.exports = {
  sendPushNotification
};