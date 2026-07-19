import { type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';

/**
 * 관리자 전용 라우트 가드. 로그인 + ADMIN role 확인.
 * 실제 보안은 서버(/api/admin/** = ROLE_ADMIN)가 담당하고, 여기선 화면 접근만 막는다.
 */
export default function AdminRoute({ children }: { children: ReactNode }) {
  const { user, ready, loadError, retry } = useAuth();

  if (!ready) {
    return (
      <div className="loading-screen">
        <div className="spinner" aria-label="불러오는 중" />
      </div>
    );
  }

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
  if (user.role !== 'ADMIN') return <Navigate to="/" replace />; // 관리자 아님 → 홈으로
  return <>{children}</>;
}
