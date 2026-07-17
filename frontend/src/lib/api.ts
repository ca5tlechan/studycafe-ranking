// ===== 백엔드 API 클라이언트 + 타입 =====

export interface School {
  id: number;
  name: string;
  shortName: string | null;
}

export interface User {
  id: number;
  loginId: string;
  displayName: string;
  nameSeq: number;
  schoolId: number | null;
  schoolName: string | null;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  user: User;
}

export interface CurrentSession {
  active: boolean;
  sessionId: number | null;
  checkInAt: string | null;
  cafeName: string | null;
}

/** 백엔드 SessionStatus(§4)와 1:1. 값이 늘면 여기부터 깨지도록 유니온으로 고정한다. */
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'AUTO_CLOSED' | 'FORCE_CLOSED';

/** 토글 결과. 카페 QR 한 장으로 체크인/체크아웃이 전환된다. */
export interface SessionToggle {
  action: 'CHECK_IN' | 'CHECK_OUT';
  sessionId: number;
  status: SessionStatus;
  checkInAt: string;
  checkOutAt: string | null;
}

export interface SignupInput {
  loginId: string;
  password: string;
  displayName: string;
  schoolId: number | null;
}

export interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

const TOKEN_KEY = 'scr.token';
export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string | null): void => {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
};

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** 인증 만료(401/403)를 앱 전역에서 한 번에 처리하기 위한 훅. AuthProvider 가 등록한다. */
export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null): void => {
  unauthorizedHandler = handler;
};

export class ApiError extends Error {
  status: number;
  body: ApiErrorBody | null;
  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? `요청에 실패했어요 (${status})`);
    this.status = status;
    this.body = body;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const res = await fetch('/api' + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  // 토큰을 실어 보낸 요청이 401/403이면 세션 만료로 간주하고 인증 상태를 정리한다.
  // (로그인 실패의 401은 토큰 없이 보낸 요청이라 여기 해당하지 않는다.)
  // 응답이 늦게 오는 사이 다른 토큰으로 로그인했을 수 있으므로, 보낸 토큰이 아직 그대로일 때만 정리한다.
  // 그러지 않으면 만료 토큰의 뒤늦은 401이 방금 발급받은 세션을 지운다.
  if (token && (res.status === 401 || res.status === 403) && getToken() === token) {
    setToken(null);
    unauthorizedHandler?.();
  }
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, body);
  return body as T;
}

export const authApi = {
  login: (loginId: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ loginId, password }),
    }),
  signup: (input: SignupInput) =>
    request<User>('/auth/signup', { method: 'POST', body: JSON.stringify(input) }),
  me: () => request<User>('/users/me'),
  schools: () => request<School[]>('/schools'),
};

export const sessionApi = {
  current: () => request<CurrentSession>('/sessions/current'),
  toggle: (cafeToken: string) =>
    request<SessionToggle>('/sessions/toggle', {
      method: 'POST',
      body: JSON.stringify({ cafeToken }),
    }),
};
