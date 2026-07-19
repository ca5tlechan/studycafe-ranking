// ===== Web Push 구독 헬퍼(§3.6b, §8.4) =====
// 서비스워커는 vite-plugin-pwa(autoUpdate)가 등록한다 — 여기선 navigator.serviceWorker.ready 로
// 그 등록을 받아 pushManager 를 다룬다. 실제 도달은 HTTPS + 설치형 PWA + 실기기에서만 검증된다.

import { pushApi } from './api';

/** 이 브라우저가 Web Push 를 지원하는가. iOS 는 설치형 PWA + 16.4+ 에서만 true 가 된다. */
export function isPushSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/** 현재 알림 권한 상태. */
export function notificationPermission(): NotificationPermission | 'unsupported' {
  return isPushSupported() ? Notification.permission : 'unsupported';
}

/** 서버에 저장된 것과 무관하게, 이 브라우저에 현재 살아있는 구독이 있는지. */
export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!isPushSupported()) return null;
  const reg = await navigator.serviceWorker.ready;
  return reg.pushManager.getSubscription();
}

/**
 * 알림을 켠다: 권한 요청 → pushManager 구독 → 서버 저장.
 * @returns 성공 여부. 권한 거부/미지원이면 false.
 */
export async function enablePush(vapidPublicKey: string): Promise<boolean> {
  if (!isPushSupported()) return false;

  // 이미 허용됐으면 다시 묻지 않는다. 거부 상태면 브라우저가 프롬프트를 막으므로 실패로 처리.
  let permission = Notification.permission;
  if (permission === 'default') {
    permission = await Notification.requestPermission();
  }
  if (permission !== 'granted') return false;

  // 구독 생성/서버 저장의 모든 실패를 여기서 삼켜 false 로 돌린다. 안 그러면 pushManager.subscribe
  // (권한 경합·applicationServerKey 불일치) 나 pushApi.subscribe(네트워크/서버 오류) 예외가
  // 호출부(PushToggle)로 전파돼, "브라우저는 구독됐는데 서버는 모르는" 상태가 조용히 남는다.
  try {
    const reg = await navigator.serviceWorker.ready;
    // 기존 구독이 있으면 재사용, 없으면 새로 만든다.
    const sub =
      (await reg.pushManager.getSubscription()) ??
      (await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      }));

    const { endpoint, keys } = sub.toJSON();
    if (!endpoint || !keys?.p256dh || !keys.auth) return false;
    await pushApi.subscribe({ endpoint, keys: { p256dh: keys.p256dh, auth: keys.auth } });
    return true;
  } catch {
    return false;
  }
}

/** 알림을 끈다: 브라우저 구독 해지 + 서버에서 제거. 서버 제거는 실패해도 배치가 410 으로 정리한다. */
export async function disablePush(): Promise<void> {
  const sub = await currentSubscription();
  if (!sub) return;
  const endpoint = sub.endpoint;
  await sub.unsubscribe();
  try {
    await pushApi.unsubscribe(endpoint);
  } catch {
    // 서버 삭제 실패는 치명적이지 않다 — 다음 발송 때 410 으로 자동 정리된다.
  }
}

/**
 * VAPID 공개키(base64url) → applicationServerKey 로 쓸 Uint8Array.
 * 반환 타입을 Uint8Array<ArrayBuffer> 로 고정한다 — pushManager.subscribe 의 applicationServerKey 는
 * ArrayBuffer 백킹 BufferSource 만 받으므로(SharedArrayBuffer 백킹은 거부) 명시적 ArrayBuffer 위에 만든다.
 */
function urlBase64ToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const output = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}
