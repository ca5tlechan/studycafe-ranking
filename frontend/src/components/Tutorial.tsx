import { useState, type ReactNode } from 'react';

const SEEN_KEY = 'scr_tutorial_seen';

// 저장소 거부 브라우저에서도 렌더가 깨지지 않게 감싼다.
function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* 저장 거부 — 무시(이번엔 다시 뜰 수 있음) */
  }
}

const svg = (children: ReactNode): ReactNode => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);

const STEPS: { icon: ReactNode; title: string; desc: string }[] = [
  {
    icon: svg(<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><path d="M14 14h3v3h-3zM20 20v.01M17 17v.01M20 14v.01M14 20v.01" /></>),
    title: 'QR로 체크인',
    desc: '카페에 있는 QR을 찍으면 공부가 시작돼요. 자리를 뜰 때 다시 찍으면 체크아웃되고 공부시간이 기록돼요.',
  },
  {
    icon: svg(<><circle cx="12" cy="12" r="8" /><path d="M12 7v5l3 2" /></>),
    title: '하루는 새벽 4시 마감',
    desc: '체크아웃을 깜빡해도 04:00에 자동으로 마감돼요. 계속 공부하려면 04:00 이후 다시 체크인해 주세요.',
  },
  {
    icon: svg(<><line x1="6" y1="20" x2="6" y2="12" /><line x1="12" y1="20" x2="12" y2="5" /><line x1="18" y1="20" x2="18" y2="14" /><line x1="3" y1="20" x2="21" y2="20" /></>),
    title: '마이에서 내 기록',
    desc: '요일별·시간대별 공부 패턴과 달력을 한눈에 볼 수 있어요. 나만의 공부 리듬을 확인해 보세요.',
  },
  {
    icon: svg(<><path d="M8 21h8M12 17v4M7 4h10v4a5 5 0 01-10 0zM5 8H4a2 2 0 01-2-2V5h3M19 8h1a2 2 0 002-2V5h-3" /></>),
    title: '랭킹에서 순위 겨루기',
    desc: '친구들·학교와 공부시간을 겨뤄봐요. 이름은 가려져서(예: 김O수) 표시되니 안심하세요.',
  },
];

/**
 * 첫 실행 사용 안내(§Phase 11). 설치형 앱은 저장소가 분리돼, 홈 화면에 추가한 뒤 처음 열 때 뜬다.
 * localStorage 플래그로 한 번만 보여주고, "건너뛰기"로 언제든 닫을 수 있다.
 */
export default function Tutorial() {
  const [visible, setVisible] = useState(() => safeGet(SEEN_KEY) !== '1');
  const [step, setStep] = useState(0);

  if (!visible) return null;

  const last = step === STEPS.length - 1;
  const close = () => {
    safeSet(SEEN_KEY, '1');
    setVisible(false);
  };
  const next = () => (last ? close() : setStep((s) => s + 1));
  const cur = STEPS[step];

  return (
    <div className="tut-overlay" role="dialog" aria-modal="true" aria-label="앱 사용 안내">
      <div className="tut-card">
        <button type="button" className="tut-skip" onClick={close}>건너뛰기</button>
        <div className="tut-icon">{cur.icon}</div>
        <h3 className="tut-title">{cur.title}</h3>
        <p className="tut-desc">{cur.desc}</p>
        <div className="tut-dots">
          {STEPS.map((_, i) => <span key={i} className={`tut-dot${i === step ? ' on' : ''}`} />)}
        </div>
        <div className="tut-actions">
          {step > 0 && (
            <button type="button" className="btn ghost" onClick={() => setStep((s) => s - 1)}>이전</button>
          )}
          <button type="button" className="btn" onClick={next}>{last ? '시작하기' : '다음'}</button>
        </div>
      </div>
    </div>
  );
}
