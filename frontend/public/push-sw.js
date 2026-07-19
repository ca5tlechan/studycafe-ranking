/* Web Push 핸들러(§3.6b). vite-plugin-pwa 가 생성하는 서비스워커에 workbox.importScripts 로 주입된다.
   페이로드 형태: { title, body, url } — 백엔드 PushMessage 와 1:1. */

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = {};
  }
  const title = data.title || '스터디카페 랭킹';
  const options = {
    body: data.body || '',
    data: { url: data.url || '/' },
    // 같은 태그로 덮어써서 03:30 알림이 여러 개 쌓이지 않게 한다.
    tag: 'pre-close',
    renotify: true,
    // 아이콘은 PWA 아이콘 등록(§manifest) 후 여기 지정. 지금은 브라우저 기본 사용.
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = (event.notification.data && event.notification.data.url) || '/';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // 이미 앱 창이 열려 있으면 그 창을 포커스하고 해당 경로로 이동.
      for (const client of clientList) {
        if ('focus' in client) {
          client.focus();
          if ('navigate' in client) {
            client.navigate(target).catch(() => {});
          }
          return undefined;
        }
      }
      // 열린 창이 없으면 새로 연다.
      if (self.clients.openWindow) {
        return self.clients.openWindow(target);
      }
      return undefined;
    }),
  );
});
