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
  /** 이번 스터디-월의 04:00 자동 마감 경고 수(§3.6c). */
  warningCount: number;
  /** 경고 임계 도달 → 이번 기간 랭킹 제외. */
  penalized: boolean;
  /** 페널티 임계값. 정책 값의 단일 진실 공급원은 서버(StudyTimePolicy)이므로 프론트는 하드코딩하지 않는다. */
  penaltyThreshold: number;
  /** 권한. ADMIN 만 관리자 화면 접근. */
  role: Role;
}

export type Role = 'USER' | 'ADMIN';


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

// ===== 마이페이지 통계(§5.1) =====

/** 이번 주/이번 달 총 공부시간. 실제 시간 그대로 — 랭킹 캡(하루 16h·주 84h) 미적용. */
export interface StatsOverview {
  weekSeconds: number;
  monthSeconds: number;
}

/** 월간 캘린더. days 는 기록이 있는 날짜만 담겨 온다(빈 날은 아예 없음). */
export interface StatsCalendar {
  year: number;
  month: number;
  days: { date: string; totalSeconds: number }[];
}

/** 백엔드가 DayOfWeek.name() 을 그대로 준다. 항상 월~일 7개. */
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** 요일별 '하루 평균' — 기록이 있는 해당 요일들의 평균이다(이번 주 breakdown 이 아니다). */
export interface StatsWeekdayPattern {
  pattern: { weekday: Weekday; avgSeconds: number }[];
}

/** 시간대별 총량. 벽시계 0~23시 24개 고정(04:00 분할 미적용). */
export interface StatsHourlyPattern {
  pattern: { hour: number; totalSeconds: number }[];
}

// ===== 랭킹(§5.2/§5.3) =====

/** 백엔드 RankingPeriod 와 1:1. 쿼리 파라미터는 이 소문자 형태 그대로 보낸다. */
export type RankingPeriod = 'this_week' | 'last_week' | 'this_month' | 'last_month' | 'this_year';

/** 개인 랭킹 한 줄. seconds 는 랭킹 캡(하루 16h·주 84h)이 적용된 값이다(마이페이지와 다를 수 있다). */
export interface RankEntry {
  rank: number;
  displayName: string; // 마스킹된 이름 — 예: 김O현(ㅁㅁ중)
  seconds: number;
  isMe: boolean;
}

/** podium=1~3위, list=4~10위, myRank=내 순위(10위 밖이어도 옴). 기간 내 기록이 없으면 myRank=null. */
export interface IndividualRanking {
  period: RankingPeriod;
  podium: RankEntry[];
  list: RankEntry[];
  myRank: RankEntry | null;
}

/** 학교 랭킹 한 줄. avgSeconds = 그 기간 활동 인원 평균. */
export interface SchoolEntry {
  rank: number;
  schoolName: string;
  memberCount: number;
  avgSeconds: number;
}

/** 최소 인원(5명) 미달 학교는 서버에서 이미 제외돼 온다. */
export interface SchoolRanking {
  period: RankingPeriod;
  podium: SchoolEntry[];
  list: SchoolEntry[];
}

/** 우리 학교 페이지. 무소속이면 available=false 이고 나머지는 null. */
export interface SchoolMine {
  available: boolean;
  schoolName: string | null;
  ranking: IndividualRanking | null;
}

export interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

// 인증은 서버가 내려주는 HttpOnly 쿠키(scr_token)로 한다(이슈 #7). JS 는 토큰을 보관하지 않는다.
// 쿠키는 credentials:'include' 로 브라우저가 자동 전송한다.

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** 인증 만료(401/403)를 앱 전역에서 한 번에 처리하기 위한 훅. AuthProvider 가 등록한다. */
export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null): void => {
  unauthorizedHandler = handler;
};

/**
 * 인증 상태 세대. 로그인/로그아웃 시 올린다. 토큰을 JS 로 볼 수 없으니, 예전의 "보낸 토큰이
 * 아직 그대로일 때만 정리" 가드를 세대 비교로 대신한다: 요청 시작 시점의 세대와 응답 시점의
 * 세대가 같을 때만 만료 처리해, 로그인 직후 도착한 이전 세션의 401 이 새 세션을 지우지 않게 한다.
 */
let authEpoch = 0;
export const bumpAuthEpoch = (): void => {
  authEpoch += 1;
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
  const epoch = authEpoch;
  const res = await fetch('/api' + path, {
    ...options,
    credentials: 'include', // HttpOnly 인증 쿠키를 함께 보낸다
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });
  // 인증이 필요한 요청이 401/403이면 세션 만료로 보고 전역 처리한다.
  // - /auth/* 의 401(로그인 실패 등)은 자격 증명 문제이지 세션 만료가 아니므로 제외한다.
  // - 요청 시작 이후 로그인/로그아웃이 있었으면(세대 변경) 이 응답은 옛 세션의 것이므로 무시한다.
  if (
    (res.status === 401 || res.status === 403)
    && !path.startsWith('/auth/')
    && epoch === authEpoch
  ) {
    unauthorizedHandler?.();
  }
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, body);
  return body as T;
}

export const authApi = {
  /** 성공 시 서버가 인증 쿠키를 Set-Cookie 로 내려주고, 본문엔 사용자 정보만 온다. */
  login: (loginId: string, password: string) =>
    request<User>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ loginId, password }),
    }),
  signup: (input: SignupInput) =>
    request<User>('/auth/signup', { method: 'POST', body: JSON.stringify(input) }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
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

export const rankingApi = {
  individual: (period: RankingPeriod) =>
    request<IndividualRanking>(`/rankings/individual?period=${period}`),
  school: (period: RankingPeriod) => request<SchoolRanking>(`/rankings/school?period=${period}`),
  schoolMine: (period: RankingPeriod) => request<SchoolMine>(`/rankings/school/mine?period=${period}`),
};

export const statsApi = {
  overview: () => request<StatsOverview>('/me/stats/overview'),
  calendar: (year: number, month: number) =>
    request<StatsCalendar>(`/me/stats/calendar?year=${year}&month=${month}`),
  weekdayPattern: () => request<StatsWeekdayPattern>('/me/stats/weekday-pattern'),
  hourlyPattern: () => request<StatsHourlyPattern>('/me/stats/hourly-pattern'),
};

// ===== 관리자(§ 관리자 페이지) — ADMIN 전용 =====

export interface AdminUser {
  id: number;
  loginId: string;
  displayName: string; // 관리자는 마스킹 없는 실명을 본다
  schoolId: number | null;
  schoolName: string | null;
  role: Role;
  warningCount: number;
  penalized: boolean;
  checkedIn: boolean;
}

export interface AdminSchool {
  id: number;
  name: string;
  shortName: string | null;
  memberCount: number;
}

export interface CafeQr {
  id: number;
  name: string;
  qrToken: string;
}

export const adminApi = {
  // 사용자
  users: () => request<AdminUser[]>('/admin/users'),
  changeRole: (id: number, role: Role) =>
    request<void>(`/admin/users/${id}/role`, { method: 'PUT', body: JSON.stringify({ role }) }),
  deleteUser: (id: number) => request<void>(`/admin/users/${id}`, { method: 'DELETE' }),
  resetWarnings: (id: number) =>
    request<void>(`/admin/users/${id}/warnings/reset`, { method: 'POST' }),
  forceCheckout: (id: number) =>
    request<void>(`/admin/users/${id}/force-checkout`, { method: 'POST' }),
  // 학교
  schools: () => request<AdminSchool[]>('/admin/schools'),
  createSchool: (name: string, shortName: string | null) =>
    request<AdminSchool>('/admin/schools', { method: 'POST', body: JSON.stringify({ name, shortName }) }),
  updateSchool: (id: number, name: string, shortName: string | null) =>
    request<AdminSchool>(`/admin/schools/${id}`, { method: 'PUT', body: JSON.stringify({ name, shortName }) }),
  deleteSchool: (id: number) => request<void>(`/admin/schools/${id}`, { method: 'DELETE' }),
  // 카페 QR / 배치
  cafes: () => request<CafeQr[]>('/admin/cafes'),
  rotateQr: (id: number) => request<CafeQr>(`/admin/cafes/${id}/rotate-qr`, { method: 'POST' }),
  runDailyClose: () => request<{ closed: number }>('/admin/batch/daily-close', { method: 'POST' }),
};

// ===== Web Push(§3.6b 03:30 사전 알림) =====

/** VAPID 공개키. enabled=false(서버에 키 미설정)면 알림 토글을 숨긴다. */
export interface VapidKey {
  enabled: boolean;
  publicKey: string | null;
}

/** 브라우저 PushSubscription.toJSON() 과 동일한 형태로 서버에 저장한다. */
export interface PushSubscriptionPayload {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

export const pushApi = {
  vapidKey: () => request<VapidKey>('/push/vapid-public-key'),
  subscribe: (sub: PushSubscriptionPayload) =>
    request<void>('/push/subscribe', { method: 'POST', body: JSON.stringify(sub) }),
  unsubscribe: (endpoint: string) =>
    request<void>('/push/unsubscribe', { method: 'POST', body: JSON.stringify({ endpoint }) }),
};
