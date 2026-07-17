import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { sessionApi, type CurrentSession } from '../lib/api';

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

const QrIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><path d="M14 14h3v3h-3zM20 14v3M17 20h4M14 20h0" /></svg>
);

export default function HomePage() {
  const { user, logout } = useAuth();
  const [session, setSession] = useState<CurrentSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setSession(await sessionApi.current());
    } catch {
      // 실패를 '기록 없음'으로 숨기지 않는다 — 구분해서 재시도를 준다.
      setFailed(true);
      setSession(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <header className="topbar">
        <div>
          <div className="hi">안녕하세요,</div>
          <h1>{user?.displayName}님</h1>
        </div>
        <button className="btn ghost" style={{ padding: '8px 13px', fontSize: 13 }} onClick={logout}>
          로그아웃
        </button>
      </header>

      <div className="app-body">
        {loading ? (
          <div className="center-msg">불러오는 중…</div>
        ) : failed ? (
          <div className="card stack">
            <span className="pill idle"><span className="dot" />불러오지 못함</span>
            <div className="state-line">공부 상태를 불러오지 못했어요.</div>
            <button className="btn" onClick={() => void load()}>다시 시도</button>
            {/* 상태를 몰라도 토글은 가능하다 — 조회 실패가 기록 자체를 막지 않도록 길을 남긴다. */}
            <Link className="btn ghost full" to="/checkin">{QrIcon}QR 체크인·체크아웃</Link>
          </div>
        ) : session?.active ? (
          <div className="card stack">
            <span className="pill studying"><span className="dot live" />공부 중</span>
            <div className="state-line">
              <b>{session.cafeName}</b>에서 공부하고 있어요
              {session.checkInAt && <> · <span className="num">{fmtTime(session.checkInAt)}</span> 시작</>}
            </div>
            <Link className="btn full" to="/checkin">{QrIcon}QR 체크아웃</Link>
          </div>
        ) : (
          <div className="card stack">
            <span className="pill idle"><span className="dot" />대기 중</span>
            <div className="state-line">지금은 진행 중인 공부 기록이 없어요.</div>
            <Link className="btn full" to="/checkin">{QrIcon}QR 체크인</Link>
          </div>
        )}
      </div>
    </>
  );
}
