import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import {
  ApiError,
  adminApi,
  type AdminSchool,
  type AdminUser,
  type CafeQr,
} from '../lib/api';

type Tab = 'users' | 'schools' | 'ops';

export default function AdminPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('users');
  const [toast, setToast] = useState<string>('');

  const toastTimer = useRef<number | undefined>(undefined);
  const notify = useCallback((msg: string) => {
    if (toastTimer.current !== undefined) window.clearTimeout(toastTimer.current);
    setToast(msg);
    toastTimer.current = window.setTimeout(() => {
      setToast('');
      toastTimer.current = undefined;
    }, 2600);
  }, []);

  useEffect(() => () => {
    if (toastTimer.current !== undefined) window.clearTimeout(toastTimer.current);
  }, []);

  return (
    <div className="shell">
      <div className="page">
        <header className="topbar">
          <div>
            <div className="hi">운영자 전용</div>
            <h1>관리자</h1>
          </div>
          <button className="btn ghost sm" onClick={() => navigate('/')}>앱으로</button>
        </header>

        <div className="app-body">
          <div className="tabs" role="tablist">
            <button role="tab" aria-selected={tab === 'users'}
              className={`tab${tab === 'users' ? ' on' : ''}`} onClick={() => setTab('users')}>사용자</button>
            <button role="tab" aria-selected={tab === 'schools'}
              className={`tab${tab === 'schools' ? ' on' : ''}`} onClick={() => setTab('schools')}>학교</button>
            <button role="tab" aria-selected={tab === 'ops'}
              className={`tab${tab === 'ops' ? ' on' : ''}`} onClick={() => setTab('ops')}>운영</button>
          </div>

          {tab === 'users' && <UsersTab meId={user?.id ?? -1} notify={notify} />}
          {tab === 'schools' && <SchoolsTab notify={notify} />}
          {tab === 'ops' && <OpsTab notify={notify} />}
        </div>
      </div>
      {toast && <div className="admin-toast" role="status">{toast}</div>}
    </div>
  );
}

/** 공통 에러 메시지 추출 */
function errMsg(e: unknown, fallback: string): string {
  return e instanceof ApiError ? (e.body?.message ?? e.message) : fallback;
}

// ---------- 사용자 ----------

function UsersTab({ meId, notify }: { meId: number; notify: (m: string) => void }) {
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [schools, setSchools] = useState<AdminSchool[]>([]); // 소속 변경 드롭다운용
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    try {
      const [us, sc] = await Promise.all([adminApi.users(), adminApi.schools()]);
      setUsers(us);
      setSchools(sc);
    } catch {
      setFailed(true);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      notify(ok);
      await load();
    } catch (e) {
      notify(errMsg(e, '작업에 실패했어요.'));
    }
  };

  if (failed) {
    return <div className="card stack">
      <div className="state-line">사용자를 불러오지 못했어요.</div>
      <button className="btn" onClick={() => void load()}>다시 시도</button>
    </div>;
  }
  if (!users) return <div className="center-msg">불러오는 중…</div>;

  return (
    <div className="admin-list">
      {users.map((u) => (
        <div key={u.id} className="admin-row">
          <div className="admin-row-main">
            <div className="admin-name">
              {u.displayName}
              {u.role === 'ADMIN' && <span className="chip chip-admin">관리자</span>}
              {u.checkedIn && <span className="chip chip-live">공부 중</span>}
              {u.penalized && <span className="chip chip-penalty">페널티</span>}
            </div>
            <div className="admin-sub">
              @{u.loginId} · {u.schoolName ?? '무소속'} · 경고 {u.warningCount}
            </div>
          </div>
          <div className="admin-actions">
            {u.role === 'ADMIN'
              ? <button className="mini" disabled={u.id === meId}
                  title={u.id === meId ? '본인은 강등할 수 없어요' : ''}
                  onClick={() => void act(() => adminApi.changeRole(u.id, 'USER'), '관리자 권한을 회수했어요')}>강등</button>
              : <button className="mini" onClick={() => void act(() => adminApi.changeRole(u.id, 'ADMIN'), '관리자로 지정했어요')}>관리자 지정</button>}
            {/* 소속 변경(전학 등) — 과거 기록도 새 학교 랭킹으로 함께 이동. */}
            <select
              className="mini-select"
              value={u.schoolId ?? ''}
              aria-label={`${u.displayName} 소속 학교 변경`}
              onChange={(e) => {
                const v = e.target.value;
                const schoolId = v === '' ? null : Number(v);
                const label = schoolId === null ? '무소속' : schools.find((s) => s.id === schoolId)?.name ?? '학교';
                void act(() => adminApi.changeUserSchool(u.id, schoolId), `${u.displayName}을(를) ${label}(으)로 옮겼어요`);
              }}
            >
              <option value="">무소속</option>
              {schools.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
            {u.warningCount > 0 &&
              <button className="mini" onClick={() => void act(() => adminApi.resetWarnings(u.id), '경고를 초기화했어요')}>경고 리셋</button>}
            {u.checkedIn &&
              <button className="mini" onClick={() => void act(() => adminApi.forceCheckout(u.id), '강제 체크아웃했어요')}>강제 체크아웃</button>}
            <button className="mini danger" disabled={u.id === meId}
              title={u.id === meId ? '본인 계정은 삭제할 수 없어요' : ''}
              onClick={() => {
                if (confirm(`${u.displayName}(@${u.loginId}) 계정을 삭제할까요? 기록도 함께 삭제됩니다.`)) {
                  void act(() => adminApi.deleteUser(u.id), '계정을 삭제했어요');
                }
              }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  );
}

// ---------- 학교 ----------

function SchoolsTab({ notify }: { notify: (m: string) => void }) {
  const [schools, setSchools] = useState<AdminSchool[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [name, setName] = useState('');
  const [shortName, setShortName] = useState('');
  const [editing, setEditing] = useState<AdminSchool | null>(null);

  const load = useCallback(async () => {
    setFailed(false);
    try {
      setSchools(await adminApi.schools());
    } catch {
      setFailed(true);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const submit = async () => {
    const n = name.trim();
    if (!n) return;
    try {
      if (editing) {
        await adminApi.updateSchool(editing.id, n, shortName.trim() || null);
        notify('학교를 수정했어요');
      } else {
        await adminApi.createSchool(n, shortName.trim() || null);
        notify('학교를 추가했어요');
      }
      setName(''); setShortName(''); setEditing(null);
      await load();
    } catch (e) {
      notify(errMsg(e, '저장에 실패했어요.'));
    }
  };

  const startEdit = (s: AdminSchool) => {
    setEditing(s); setName(s.name); setShortName(s.shortName ?? '');
  };

  const remove = async (s: AdminSchool) => {
    if (!confirm(`${s.name}을(를) 삭제할까요? 소속 학생 ${s.memberCount}명은 무소속이 됩니다.`)) return;
    try {
      await adminApi.deleteSchool(s.id);
      notify('학교를 삭제했어요');
      if (editing?.id === s.id) { setEditing(null); setName(''); setShortName(''); }
      await load();
    } catch (e) {
      notify(errMsg(e, '삭제에 실패했어요.'));
    }
  };

  return (
    <>
      <div className="card">
        <div className="lbl">{editing ? `학교 수정: ${editing.name}` : '학교 추가'}</div>
        <div className="admin-form">
          <input className="input" placeholder="학교 이름 (예: 서울대학교)" value={name}
            onChange={(e) => setName(e.target.value)} />
          <input className="input" placeholder="축약명 (예: 서울대, 선택)" value={shortName}
            onChange={(e) => setShortName(e.target.value)} />
          <div className="admin-form-actions">
            <button className="btn" disabled={!name.trim()} onClick={() => void submit()}>
              {editing ? '수정' : '추가'}
            </button>
            {editing && <button className="btn ghost" onClick={() => { setEditing(null); setName(''); setShortName(''); }}>취소</button>}
          </div>
        </div>
      </div>

      {failed ? (
        <div className="card stack">
          <div className="state-line">학교를 불러오지 못했어요.</div>
          <button className="btn" onClick={() => void load()}>다시 시도</button>
        </div>
      ) : !schools ? (
        <div className="center-msg">불러오는 중…</div>
      ) : (
        <div className="admin-list">
          {schools.map((s) => (
            <div key={s.id} className="admin-row">
              <div className="admin-row-main">
                <div className="admin-name">{s.name}{s.shortName && <span className="admin-sub"> ({s.shortName})</span>}</div>
                <div className="admin-sub">소속 {s.memberCount}명</div>
              </div>
              <div className="admin-actions">
                <button className="mini" onClick={() => startEdit(s)}>수정</button>
                <button className="mini danger" onClick={() => void remove(s)}>삭제</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

// ---------- 운영 (카페 QR + 배치) ----------

function OpsTab({ notify }: { notify: (m: string) => void }) {
  const [cafes, setCafes] = useState<CafeQr[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    try {
      setCafes(await adminApi.cafes());
    } catch {
      setFailed(true);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const rotate = async (c: CafeQr) => {
    if (!confirm(`${c.name}의 QR을 재발급할까요? 기존 QR은 즉시 무효가 되어 새 QR을 출력·부착해야 합니다.`)) return;
    try {
      await adminApi.rotateQr(c.id);
      notify('QR을 재발급했어요. 새 QR을 부착하세요');
      await load();
    } catch (e) {
      notify(errMsg(e, 'QR 재발급에 실패했어요.'));
    }
  };

  const runBatch = async () => {
    if (!confirm('모든 활성 세션을 04:00(KST) 기준으로 마감합니다. 집계·경고 적립까지 즉시 처리되며 되돌릴 수 없어요. 실행할까요?')) return;
    setRunning(true);
    try {
      const res = await adminApi.runDailyClose();
      notify(`04:00 마감 실행 완료 — ${res.closed}건 종료`);
    } catch (e) {
      notify(errMsg(e, '배치 실행에 실패했어요.'));
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <div className="lbl" style={{ marginBottom: 8 }}>카페 QR</div>
      {failed ? (
        <div className="card stack">
          <div className="state-line">카페를 불러오지 못했어요.</div>
          <button className="btn" onClick={() => void load()}>다시 시도</button>
        </div>
      ) : !cafes ? (
        <div className="center-msg">불러오는 중…</div>
      ) : (
        <div className="admin-list">
          {cafes.map((c) => (
            <div key={c.id} className="admin-row">
              <div className="admin-row-main">
                <div className="admin-name">{c.name}</div>
                <div className="admin-sub num">{c.qrToken}</div>
              </div>
              <div className="admin-actions">
                <button className="mini" onClick={() => void rotate(c)}>QR 재발급</button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="card" style={{ marginTop: 16 }}>
        <div className="lbl">04:00 자동 마감</div>
        <p className="chart-sub">지금 열려 있는 세션을 각자의 스터디 날짜 04:00에 마감합니다. 배치가 걸렀을 때 수동 복구용이에요.</p>
        <button className="btn" disabled={running} onClick={() => void runBatch()}>
          {running ? '실행 중…' : '지금 마감 실행'}
        </button>
      </div>
    </>
  );
}
