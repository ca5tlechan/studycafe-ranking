import { type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, ready, loadError, retry } = useAuth();

  if (!ready) {
    return (
      <div className="loading-screen">
        <div className="spinner" aria-label="불러오는 중" />
      </div>
    );
  }

  // 네트워크/서버 오류 — 토큰이 아직 유효할 수 있으니 로그인으로 내보내지 않고 재시도를 준다.
  if (loadError) {
    return (
      <div className="loading-screen">
        <div className="center-msg">
          연결에 문제가 있어 정보를 불러오지 못했어요.
          <div style={{ marginTop: 16 }}>
            <button className="btn" onClick={retry}>다시 시도</button>
          </div>
        </div>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
