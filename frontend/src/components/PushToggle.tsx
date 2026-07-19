import { useEffect, useState } from 'react';
import { pushApi } from '../lib/api';
import { currentSubscription, disablePush, enablePush, isPushSupported } from '../lib/push';

type State = 'loading' | 'unavailable' | 'off' | 'on' | 'denied';

/**
 * 03:30 마감 알림(§3.6b) 켜기/끄기. 서버에 VAPID 키가 없거나(enabled=false) 브라우저가 Web Push 를
 * 지원하지 않으면(iOS 는 설치형 PWA + 16.4+ 필요) 아무것도 렌더하지 않는다 — 못 켜는 걸 노출하지 않는다.
 */
export default function PushToggle() {
  const [state, setState] = useState<State>('loading');
  const [publicKey, setPublicKey] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    void (async () => {
      if (!isPushSupported()) {
        if (alive) setState('unavailable');
        return;
      }
      try {
        const vk = await pushApi.vapidKey();
        if (!alive) return;
        if (!vk.enabled || !vk.publicKey) {
          setState('unavailable');
          return;
        }
        setPublicKey(vk.publicKey);
        if (Notification.permission === 'denied') {
          setState('denied');
          return;
        }
        const sub = await currentSubscription();
        if (alive) setState(sub ? 'on' : 'off');
      } catch {
        if (alive) setState('unavailable');
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  if (state === 'loading' || state === 'unavailable') return null;

  const toggle = async () => {
    if (busy || !publicKey) return;
    setBusy(true);
    try {
      if (state === 'on') {
        await disablePush();
        setState('off');
      } else {
        const ok = await enablePush(publicKey);
        // 권한을 거부하면 브라우저가 더는 프롬프트를 띄우지 않는다 — 상태로 안내를 바꾼다.
        setState(ok ? 'on' : Notification.permission === 'denied' ? 'denied' : 'off');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card push-card">
      <div className="push-row">
        <div>
          <div className="lbl">03:30 마감 알림</div>
          <p className="chart-sub push-desc">
            {state === 'denied'
              ? '브라우저 알림이 차단돼 있어요. 사이트 설정에서 허용해 주세요.'
              : '자정을 넘겨 공부 중이면, 04:00 마감 전에 다시 체크인하라고 알려드려요.'}
          </p>
        </div>
        {state !== 'denied' && (
          <button className="btn sm ghost" disabled={busy} onClick={() => void toggle()}>
            {busy ? '…' : state === 'on' ? '끄기' : '켜기'}
          </button>
        )}
      </div>
    </section>
  );
}
