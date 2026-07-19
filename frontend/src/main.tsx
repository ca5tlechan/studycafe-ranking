import { StrictMode, Suspense, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import './index.css';
import { AuthProvider } from './lib/auth';
import ProtectedRoute from './components/ProtectedRoute';
import AdminRoute from './components/AdminRoute';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import HomePage from './pages/HomePage';
import RankingPage from './pages/RankingPage';
import SchoolPage from './pages/SchoolPage';
import { AdminPage, CheckInPage, MyPage } from './pages/lazy';

const lazyRoute = (node: ReactNode) => (
  <Suspense
    fallback={
      <div className="loading-screen">
        <div className="spinner" aria-label="불러오는 중" />
      </div>
    }
  >
    {node}
  </Suspense>
);

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/checkin', element: lazyRoute(<CheckInPage />) },
      { path: '/my', element: lazyRoute(<MyPage />) },
      { path: '/ranking', element: <RankingPage /> },
      { path: '/school', element: <SchoolPage /> },
    ],
  },
  {
    // 관리자 화면은 하단탭 레이아웃 밖의 독립 화면 + ADMIN 가드.
    path: '/admin',
    element: (
      <AdminRoute>
        {lazyRoute(<AdminPage />)}
      </AdminRoute>
    ),
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
);
