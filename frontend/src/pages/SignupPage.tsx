import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { AutoLoginFailedError } from '../lib/authErrors';
import { ApiError, authApi, type School } from '../lib/api';

const Logo = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round"><path d="M12 7v5l3 2" /><circle cx="12" cy="12" r="8" /></svg>
);

export default function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [schools, setSchools] = useState<School[]>([]);
  const [schoolsFailed, setSchoolsFailed] = useState(false);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [schoolId, setSchoolId] = useState(''); // '' = 무소속
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(''); // 가입 성공 + 자동 로그인만 실패
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const loadSchools = useCallback(async () => {
    setSchoolsFailed(false);
    try {
      setSchools(await authApi.schools());
    } catch {
      setSchoolsFailed(true); // 조용히 삼키지 않고 알린다 (무소속 가입은 계속 가능)
    }
  }, []);

  useEffect(() => {
    void loadSchools();
  }, [loadSchools]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setNotice('');
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
      if (err instanceof AutoLoginFailedError) {
        setNotice(err.message); // 계정은 만들어졌으니 재가입이 아니라 로그인으로 안내
      } else if (err instanceof ApiError) {
        setFieldErrors(err.body?.fieldErrors ?? {});
        setError(err.body?.fieldErrors ? '입력값을 확인해 주세요.' : err.message);
      } else {
        setError('회원가입에 실패했어요. 다시 시도해 주세요.');
      }
    } finally {
      setBusy(false);
    }
  };

  const done = Boolean(notice); // 가입 완료됨 → 다시 제출하면 중복 오류

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
        {notice && <div className="banner info" role="status">{notice}</div>}

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
          <select id="school" className="select" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}
            disabled={schoolsFailed}>
            <option value="">무소속</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          {schoolsFailed ? (
            <span className="err-txt">
              학교 목록을 불러오지 못했어요. 무소속으로는 가입할 수 있어요.{' '}
              <button type="button" className="link-btn" onClick={() => void loadSchools()}>다시 시도</button>
            </span>
          ) : (
            <span className="hint-txt">목록에 없으면 무소속을 선택하세요. 학교 랭킹에만 영향을 줘요.</span>
          )}
        </div>

        <div className="actions">
          <button className="btn full" disabled={busy || done || !loginId || !password || !displayName}>
            {busy ? '가입 중…' : '회원가입'}
          </button>
          <p className="switch">
            {done ? '가입이 끝났어요. ' : '이미 계정이 있나요? '}
            <Link to="/login">로그인</Link>
          </p>
        </div>
      </form>
    </div>
  );
}
