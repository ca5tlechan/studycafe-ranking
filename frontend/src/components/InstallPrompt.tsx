import { useSyncExternalStore } from 'react';

/** Chromium 계열 설치 프롬프트 이벤트(표준 lib 에 타입 없음). prompt() 는 사용자 선택으로 resolve. */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISS_KEY = 'scr_install_dismissed';

// 저장소 접근이 거부되는 브라우저(프라이빗 모드 등)에서 렌더가 깨지지 않게 감싼다.
function safeGet(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}
function safeSet(key: string, value: string): void {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    /* 저장 거부 — 무시(안내는 그대로 닫힌다) */
  }
}

const isIOS =
  /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  // iPadOS 데스크톱 모드는 UA 가 Macintosh 라 정규식에 안 걸린다 → 터치 가능한 Mac 을 iPad 로 본다.
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const isAndroid = /android/i.test(navigator.userAgent);

// ── 모듈 싱글턴: 로그인↔홈 라우트 전환으로 컴포넌트가 리마운트돼도 상태가 유지되게 한다 ──
let deferredPrompt: BeforeInstallPromptEvent | null = null;
let dismissed = safeGet(DISMISS_KEY) === '1';
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

if (isAndroid) {
  // 데스크톱에선 가로채지 않는다 — preventDefault 하면 브라우저 자체 설치 UI 까지 죽는다.
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e as BeforeInstallPromptEvent;
    emit();
  });
}
window.addEventListener('appinstalled', () => {
  deferredPrompt = null;
  dismissed = true; // 설치 완료 → 배너 숨김
  emit();
});

function subscribe(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}
/** deferred 유무 + dismissed 여부. 값이 바뀌면 리렌더된다. */
function snapshot(): string {
  return (deferredPrompt ? 'D' : '-') + (dismissed ? 'X' : '-');
}

function standalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as { standalone?: boolean }).standalone === true
  );
}

/** iOS 공유 시트에서 탭할 항목 미리보기(공유 아이콘 → "홈 화면에 추가"). 실제 스크린샷 대신 일러스트. */
function IosGuide() {
  return (
    <svg className="install-ill" viewBox="0 0 260 132" role="img" aria-label="아이폰 홈 화면 추가 안내">
      <rect x="8" y="8" width="244" height="44" rx="11" fill="var(--surface)" stroke="var(--line)" />
      <g stroke="var(--primary)" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
        <path d="M30 38v-15" />
        <path d="M24 27l6-6 6 6" />
        <path d="M22 33v7a2 2 0 002 2h12a2 2 0 002-2v-7" />
      </g>
      <text x="52" y="35" className="install-ill-t">하단 <tspan fontWeight="700">공유</tspan> 버튼 탭</text>
      <path d="M130 56v14" stroke="var(--muted)" strokeWidth="2" fill="none" strokeLinecap="round" />
      <path d="M125 66l5 5 5-5" stroke="var(--muted)" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="8" y="80" width="244" height="44" rx="11" fill="var(--primary-soft)" stroke="var(--primary)" />
      <text x="24" y="107" className="install-ill-h">홈 화면에 추가</text>
      <g stroke="var(--primary-ink)" strokeWidth="2" fill="none" strokeLinecap="round">
        <rect x="216" y="90" width="24" height="24" rx="6" />
        <path d="M228 96v12M222 102h12" />
      </g>
    </svg>
  );
}

/**
 * 홈 화면 설치 안내(§Phase 11). 브라우저에서(설치 전)만, 모바일에서만 뜬다.
 * 안드로이드는 원탭 설치 버튼(beforeinstallprompt), 아이폰은 그림+단계 안내(애플이 자동설치를 막음).
 * "닫기"는 이번 세션만 숨긴다 — 다음 방문 때 다시 뜨고, 브라우저 메뉴로도 언제든 설치 가능.
 */
export default function InstallPrompt() {
  useSyncExternalStore(subscribe, snapshot, snapshot); // 싱글턴 상태 변화 시 리렌더

  if (standalone() || dismissed) return null;
  if (!isIOS && !isAndroid) return null; // 데스크톱은 안내하지 않는다

  const dismiss = () => {
    dismissed = true;
    safeSet(DISMISS_KEY, '1');
    emit();
  };

  const install = async () => {
    if (!deferredPrompt) return;
    await deferredPrompt.prompt(); // 결과(outcome)는 쓰지 않는다 — appinstalled 로 설치 확정을 잡는다
    deferredPrompt = null; // 프롬프트는 한 번만 쓸 수 있다
    emit();
  };

  return (
    <section className="install-card">
      <button type="button" className="install-x" onClick={dismiss} aria-label="닫기">✕</button>
      <div className="install-title">홈 화면에 앱으로 추가</div>
      {isAndroid ? (
        <>
          <p className="install-desc">한 번 설치하면 앱처럼 빠르게 열려요.</p>
          {deferredPrompt ? (
            <button className="btn full" onClick={() => void install()}>앱 설치하기</button>
          ) : (
            <p className="install-hint">
              설치 버튼이 안 보이면 <b>브라우저 메뉴에서 “앱 설치”</b>(또는 “홈 화면에 추가”)를 선택하세요.
            </p>
          )}
        </>
      ) : (
        <>
          <p className="install-desc">Safari에서 아래 순서로 추가하세요.</p>
          <IosGuide />
        </>
      )}
    </section>
  );
}
