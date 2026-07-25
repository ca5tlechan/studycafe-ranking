import { useEffect, useState } from 'react';

/** Chrome 등에서 발생하는 설치 프롬프트 이벤트(표준 lib 에 타입이 없어 최소 정의). */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISS_KEY = 'scr_install_dismissed';

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
      {/* 1) 하단 공유 버튼 */}
      <rect x="8" y="8" width="244" height="44" rx="11" fill="var(--surface-2)" stroke="var(--line)" />
      <g stroke="var(--primary)" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
        <path d="M30 38v-15" />
        <path d="M24 27l6-6 6 6" />
        <path d="M22 33v7a2 2 0 002 2h12a2 2 0 002-2v-7" />
      </g>
      <text x="52" y="35" className="install-ill-t">하단 <tspan fontWeight="700">공유</tspan> 버튼 탭</text>
      {/* 화살표 */}
      <path d="M130 56v14" stroke="var(--muted)" strokeWidth="2" fill="none" strokeLinecap="round" />
      <path d="M125 66l5 5 5-5" stroke="var(--muted)" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      {/* 2) "홈 화면에 추가" 행 */}
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
 * "나중에"는 이번 세션만 숨긴다 — 다음 방문 때 다시 뜨고, 브라우저 메뉴로도 언제든 설치 가능.
 */
export default function InstallPrompt() {
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState(() => sessionStorage.getItem(DISMISS_KEY) === '1');
  const [installed, setInstalled] = useState(standalone());

  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const isAndroid = /android/i.test(navigator.userAgent);

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setDeferred(e as BeforeInstallPromptEvent);
    };
    const onInstalled = () => setInstalled(true);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  if (installed || dismissed) return null;
  if (!isIOS && !isAndroid) return null; // 데스크톱은 안내하지 않는다

  const dismiss = () => {
    sessionStorage.setItem(DISMISS_KEY, '1');
    setDismissed(true);
  };

  const install = async () => {
    if (!deferred) return;
    await deferred.prompt();
    await deferred.userChoice;
    setDeferred(null); // 프롬프트는 한 번만 쓸 수 있다
  };

  return (
    <section className="install-card">
      <button type="button" className="install-x" onClick={dismiss} aria-label="닫기">✕</button>
      <div className="install-title">홈 화면에 앱으로 추가</div>
      {isAndroid ? (
        <>
          <p className="install-desc">한 번 설치하면 앱처럼 빠르게 열려요.</p>
          {deferred ? (
            <button className="btn full" onClick={() => void install()}>앱 설치하기</button>
          ) : (
            <p className="install-hint">
              설치 버튼이 안 보이면 <b>Chrome 메뉴(⋮) → “앱 설치”</b>로 추가할 수 있어요.
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
