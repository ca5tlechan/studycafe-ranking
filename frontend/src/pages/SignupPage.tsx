import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { ApiError, authApi, type School } from '../lib/api';

const Logo = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round"><path d="M12 7v5l3 2" /><circle cx="12" cy="12" r="8" /></svg>
);

export default function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [schools, setSchools] = useState<School[]>([]);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [schoolId, setSchoolId] = useState(''); // '' = 무소속
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authApi.schools().then(setSchools).catch(() => { /* 선택 목록 없이도 무소속 가입 가능 */ });
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setBusy(true);
    try {
      await signup({
        loginId: loginId.trim(),
        password,
        displayName: displayName.trim(),
        schoolId: schoolId ? Number(schoolId) : null,
      });
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.body?.fieldErrors ?? {});
        setError(err.body?.fieldErrors ? '입력값을 확인해 주세요.' : err.message);
      } else {
        setError('회원가입에 실패했어요. 다시 시도해 주세요.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <div className="brand">
        <span className="logo">{Logo}</span>
        <b>스터디카페 랭킹</b>
      </div>
      <h2>시작하기</h2>
      <p className="lead">몇 가지만 입력하면 바로 기록을 시작할 수 있어요.</p>

      <form onSubmit={submit} noValidate>
        {error && <div className="banner" role="alert">{error}</div>}

        <div className="field">
          <label htmlFor="loginId">아이디</label>
          <input id="loginId" className={`input${fieldErrors.loginId ? ' err' : ''}`} value={loginId}
            autoComplete="username" onChange={(e) => setLoginId(e.target.value)} placeholder="3~30자" />
          {fieldErrors.loginId && <span className="err-txt">{fieldErrors.loginId}</span>}
        </div>

        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input id="password" type="password" className={`input${fieldErrors.password ? ' err' : ''}`} value={password}
            autoComplete="new-password" onChange={(e) => setPassword(e.target.value)} placeholder="8자 이상" />
          {fieldErrors.password && <span className="err-txt">{fieldErrors.password}</span>}
        </div>

        <div className="field">
          <label htmlFor="displayName">이름 (실명)</label>
          <input id="displayName" className={`input${fieldErrors.displayName ? ' err' : ''}`} value={displayName}
            onChange={(e) => setDisplayName(e.target.value)} placeholder="예: 김민현" />
          {fieldErrors.displayName
            ? <span className="err-txt">{fieldErrors.displayName}</span>
            : <span className="hint-txt">랭킹에는 <b>김O현</b>처럼 가운데를 가려 표시돼요.</span>}
        </div>

        <div className="field">
          <label htmlFor="school">학교</label>
          <select id="school" className="select" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}>
            <option value="">무소속</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          <span className="hint-txt">목록에 없으면 무소속을 선택하세요. 학교 랭킹에만 영향을 줘요.</span>
        </div>

        <div className="actions">
          <button className="btn full" disabled={busy || !loginId || !password || !displayName}>
            {busy ? '가입 중…' : '회원가입'}
          </button>
          <p className="switch">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
        </div>
      </form>
    </div>
  );
}
