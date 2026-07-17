import { useEffect, useRef, useState } from 'react';
import type { RankingPeriod } from '../lib/api';

/** §5.2 — 기본 이번주, 누르면 나머지 옵션이 펼쳐진다. 과거 기간 조회 포함. */
const PERIODS: { key: RankingPeriod; label: string }[] = [
  { key: 'this_week', label: '이번주' },
  { key: 'last_week', label: '지난주' },
  { key: 'this_month', label: '이번달' },
  { key: 'last_month', label: '지난달' },
  { key: 'this_year', label: '올해' },
];

export default function PeriodFilter({ value, onChange }: {
  value: RankingPeriod;
  onChange: (p: RankingPeriod) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const current = PERIODS.find((p) => p.key === value) ?? PERIODS[0];

  // 펼친 목록은 아래 내용을 덮는다 — 바깥을 누르거나 Esc 를 누르면 닫혀야 한다.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div className="period" ref={rootRef}>
      <button
        type="button"
        className="period-btn"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        {current.label}
        <span className={`caret${open ? ' up' : ''}`} aria-hidden="true">⌄</span>
      </button>
      {open && (
        <div className="period-opts">
          {PERIODS.map((p) => (
            <button
              key={p.key}
              type="button"
              className={`period-opt${p.key === value ? ' on' : ''}`}
              aria-current={p.key === value}
              onClick={() => {
                onChange(p.key);
                setOpen(false);
              }}
            >
              {p.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
