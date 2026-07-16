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
  if (token && (res.status === 401 || res.status === 403)) {
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
};
