import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { ApiError } from '../lib/api';

const Logo = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round"><path d="M12 7v5l3 2" /><circle cx="12" cy="12" r="8" /></svg>
);

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(loginId.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했어요. 다시 시도해 주세요.');
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
      <h2>다시 오셨네요</h2>
      <p className="lead">아이디로 로그인하고 오늘도 기록을 쌓아요.</p>

      <form onSubmit={submit} noValidate>
        {error && <div className="banner" role="alert">{error}</div>}
        <div className="field">
          <label htmlFor="loginId">아이디</label>
          <input id="loginId" className="input" value={loginId} autoComplete="username"
            onChange={(e) => setLoginId(e.target.value)} placeholder="아이디" />
        </div>
        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input id="password" type="password" className="input" value={password} autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" />
        </div>
        <div className="actions">
          <button className="btn full" disabled={busy || !loginId || !password}>
            {busy ? '로그인 중…' : '로그인'}
          </button>
          <p className="switch">아직 계정이 없나요? <Link to="/signup">회원가입</Link></p>
        </div>
      </form>
    </div>
  );
}
