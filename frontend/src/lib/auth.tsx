import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { authApi, getToken, setToken, type SignupInput, type User } from './api';

interface AuthContextValue {
  user: User | null;
  ready: boolean; // 초기 토큰 검증 완료 여부
  login: (loginId: string, password: string) => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken()) {
      setReady(true);
      return;
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => setToken(null)) // 만료/무효 토큰 정리
      .finally(() => setReady(true));
  }, []);

  const login = async (loginId: string, password: string) => {
    const res = await authApi.login(loginId, password);
    setToken(res.token);
    setUser(res.user);
  };

  const signup = async (input: SignupInput) => {
    await authApi.signup(input);
    await login(input.loginId, input.password); // 가입 직후 자동 로그인
  };

  const logout = () => {
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, ready, login, signup, logout }}>
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
