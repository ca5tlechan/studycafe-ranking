import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import {
  ApiError, authApi, getToken, setToken, setUnauthorizedHandler,
  type SignupInput, type User,
} from './api';
import { AutoLoginFailedError } from './authErrors';

interface AuthContextValue {
  user: User | null;
  ready: boolean;       // 초기 토큰 검증 완료 여부
  loadError: boolean;   // 네트워크/서버 오류로 검증 실패 (토큰 유지, 재시도 가능)
  login: (loginId: string, password: string) => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  logout: () => void;
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [loadError, setLoadError] = useState(false);

  const bootstrap = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      setLoadError(false);
      setReady(true);
      return;
    }
    setReady(false);
    setLoadError(false);
    try {
      setUser(await authApi.me());
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setToken(null); // 만료/무효 토큰만 정리 → 로그인 화면으로
        setUser(null);
      } else {
        setLoadError(true); // 네트워크/5xx 는 토큰 유지하고 재시도 가능하게
      }
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  // 인증 만료를 한 곳에서: api 계층이 401/403에서 토큰을 지우고, 여기서 사용자 상태를 비운다.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = async (loginId: string, password: string) => {
    const res = await authApi.login(loginId, password);
    setToken(res.token);
    setUser(res.user);
    setLoadError(false);
  };

  const signup = async (input: SignupInput) => {
    await authApi.signup(input); // 여기서 실패하면 '가입 실패'로 그대로 전파
    try {
      await login(input.loginId, input.password);
    } catch {
      throw new AutoLoginFailedError(); // 계정은 만들어졌으므로 재가입을 유도하면 안 된다
    }
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    setLoadError(false);
  };

  return (
    <AuthContext.Provider
      value={{ user, ready, loadError, login, signup, logout, retry: () => void bootstrap() }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있어요.');
  return ctx;
}
