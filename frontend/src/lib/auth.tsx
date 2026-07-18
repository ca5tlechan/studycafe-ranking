import {
  createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode,
} from 'react';
import {
  ApiError, authApi, bumpAuthEpoch, setUnauthorizedHandler,
  type SignupInput, type User,
} from './api';
import { AutoLoginFailedError } from './authErrors';

interface AuthContextValue {
  user: User | null;
  ready: boolean;       // 초기 토큰 검증 완료 여부
  loadError: boolean;   // 네트워크/서버 오류로 검증 실패 (토큰 유지, 재시도 가능)
  login: (loginId: string, password: string) => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  logout: () => Promise<void>;
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [loadError, setLoadError] = useState(false);

  // 진행 중인 부트스트랩의 세대 번호. 로그인/로그아웃/만료로 인증 상태가 확정되면
  // 번호를 올려, 늦게 도착한 검증 결과가 확정된 상태를 덮어쓰지 못하게 한다.
  const bootstrapSeq = useRef(0);
  const invalidateBootstrap = useCallback(() => {
    bootstrapSeq.current += 1;
  }, []);

  // 진행 중인 로그아웃 요청. 로그아웃의 Set-Cookie(Max-Age=0)가 뒤늦게 도착해 새 로그인 쿠키를
  // 지우지 않도록, 로그인은 이 요청이 끝난(=쿠키 삭제가 적용된) 뒤에 진행한다.
  const pendingLogout = useRef<Promise<void> | null>(null);

  const bootstrap = useCallback(async () => {
    const seq = bootstrapSeq.current + 1;
    bootstrapSeq.current = seq;
    const stale = () => bootstrapSeq.current !== seq;

    // 쿠키는 JS 로 볼 수 없으니 로그인 여부를 미리 알 수 없다 — 항상 me() 로 확인한다.
    setReady(false);
    setLoadError(false);
    try {
      const me = await authApi.me();
      if (stale()) return;
      setUser(me);
    } catch (err) {
      if (stale()) return;
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setUser(null); // 쿠키 없음/만료 → 로그인 화면으로
      } else {
        setLoadError(true); // 네트워크/5xx 는 재시도 가능하게
      }
    } finally {
      if (!stale()) setReady(true);
    }
  }, []);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  // 인증 만료를 한 곳에서: api 계층이 401/403에서 토큰을 지우고, 여기서 사용자 상태를 비운다.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      invalidateBootstrap();
      setUser(null);
      // 만료는 '확정된' 상태다. 이전 검증이 남긴 연결 오류를 지우지 않으면
      // 토큰이 정리됐는데도 화면이 로그인 대신 '연결 문제'에 머문다.
      setLoadError(false);
      setReady(true);
    });
    return () => setUnauthorizedHandler(null);
  }, [invalidateBootstrap]);

  const login = async (loginId: string, password: string) => {
    // 진행 중인 로그아웃이 있으면 그 쿠키 삭제가 적용된 뒤에 로그인해야, 늦게 온 Max-Age=0 이
    // 방금 발급받은 쿠키를 지우지 않는다.
    if (pendingLogout.current) await pendingLogout.current;
    const me = await authApi.login(loginId, password); // 서버가 인증 쿠키를 내려준다
    invalidateBootstrap(); // 진행 중이던 이전 검증이 새 세션을 덮어쓰지 않도록
    bumpAuthEpoch();        // 늦게 도착할 이전 세션의 401 을 무시하도록(api 계층 가드)
    setUser(me);
    setLoadError(false);
    setReady(true);
  };

  const signup = async (input: SignupInput) => {
    await authApi.signup(input); // 여기서 실패하면 '가입 실패'로 그대로 전파
    try {
      await login(input.loginId, input.password);
    } catch {
      throw new AutoLoginFailedError(); // 계정은 만들어졌으므로 재가입을 유도하면 안 된다
    }
  };

  const logout = (): Promise<void> => {
    invalidateBootstrap();
    bumpAuthEpoch();
    // 로컬 상태는 즉시 정리(응답을 기다리지 않고 화면 전환). 만료 토큰이면 서버가 401 이지만
    // 그 쿠키는 이미 무효라 무시해도 된다(로그아웃은 /auth/* 라 전역 401 핸들러도 타지 않는다).
    setUser(null);
    setLoadError(false);
    setReady(true);
    // 연속 로그아웃도 직렬화한다: 앞선 로그아웃 뒤에 체인으로 연결해, login 이 이 꼬리를 기다리면
    // 모든 로그아웃의 Set-Cookie(Max-Age=0) 가 적용된 뒤에야 새 쿠키를 발급받는다.
    // (마지막 것만 기다리면, 먼저 보낸 로그아웃의 삭제 응답이 재로그인 뒤 도착해 새 쿠키를 지운다.)
    const previous = pendingLogout.current ?? Promise.resolve();
    const p = previous
      .catch(() => undefined)
      .then(() => authApi.logout())
      .catch(() => { /* 만료/네트워크 실패 무시 */ })
      .finally(() => {
        if (pendingLogout.current === p) pendingLogout.current = null;
      });
    pendingLogout.current = p;
    return p;
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
