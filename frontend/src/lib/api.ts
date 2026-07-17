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
