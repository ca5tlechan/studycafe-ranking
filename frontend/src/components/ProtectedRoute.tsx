import { type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  if (!ready) {
    return (
      <div className="loading-screen">
        <div className="spinner" aria-label="불러오는 중" />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
