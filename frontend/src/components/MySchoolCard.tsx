import { useEffect, useState } from 'react';
import { authApi, type School } from '../lib/api';
import { useAuth } from '../lib/auth';

/**
 * 마이페이지 — 본인 소속(학교) 변경(전학·중→고 진학 대응). 바꾸면 과거 기록도 새 학교 랭킹으로 함께 이동한다.
 * 학교 목록 로드가 실패해도(무소속만 선택 가능) 화면을 막지 않는다.
 */
export default function MySchoolCard() {
  const { user, refresh } = useAuth();
  const [schools, setSchools] = useState<School[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');

  useEffect(() => {
    let alive = true;
    authApi.schools().then((s) => alive && setSchools(s)).catch(() => {});
    return () => { alive = false; };
  }, []);

  if (!user) return null;

  const change = async (schoolId: number | null) => {
    if (busy || schoolId === (user.schoolId ?? null)) return;
    const label = schoolId === null ? '무소속' : schools.find((s) => s.id === schoolId)?.name ?? '학교';
    if (!confirm(`소속을 ${label}(으)로 바꿀까요? 그동안의 공부 기록도 새 학교 랭킹으로 함께 옮겨져요.`)) return;
    setBusy(true);
    setMsg('');
    try {
      await authApi.changeMySchool(schoolId);
      await refresh();
      setMsg(`${label}(으)로 바꿨어요.`);
    } catch {
      setMsg('변경에 실패했어요. 잠시 뒤 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card push-card">
      <div className="push-row">
        <div>
          <div className="lbl">내 소속 학교</div>
          <p className="chart-sub push-desc">
            현재 <b>{user.schoolName ?? '무소속'}</b> · 바꾸면 학교 랭킹도 새 학교로 함께 이동해요.
          </p>
        </div>
        <select
          className="mini-select"
          value={user.schoolId ?? ''}
          disabled={busy}
          aria-label="소속 학교 변경"
          onChange={(e) => void change(e.target.value === '' ? null : Number(e.target.value))}
        >
          <option value="">무소속</option>
          {schools.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>
      {msg && (
        <p className="chart-sub push-desc" role="status">{msg}</p>
      )}
    </section>
  );
}
