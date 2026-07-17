// 표시용 포맷 — 같은 값을 화면마다 다르게 보여주면 안 되므로 한 곳에 둔다.

/**
 * 시각 표시는 KST 로 고정한다. 백엔드는 스터디 날짜·경계를 전부 KST 로 계산하므로
 * (StudyClock: "04:00 기준 스터디 날짜/경계 계산 (전부 KST)"), 기기 타임존을 따르면
 * 기기가 KST 가 아닐 때 "15:00부터 기록을 시작했어요"와 "하루는 새벽 4시에 마감돼요"가
 * 서로 어긋난 시각을 가리키게 된다.
 */
export const fmtTime = (iso: string): string =>
  new Date(iso).toLocaleTimeString('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

/** cafeName 은 타입상 null 이 가능하다. 빈칸으로 새면 "지금 에서 공부 중이에요"가 된다. */
export const CAFE_FALLBACK = '현재 카페';

/**
 * 지금의 '스터디 날짜'(§3.1 — KST 04:00 기준). 새벽 2시면 아직 어제가 오늘이다.
 * 백엔드 StudyClock.studyDateOf 와 같은 규칙이라, 캘린더의 '오늘' 칸이 집계와 어긋나지 않는다.
 */
export const studyTodayISO = (): string =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(Date.now() - 4 * 60 * 60 * 1000)); // en-CA = YYYY-MM-DD

/**
 * 초 → "HH시간 MM분" (§5.1 캘린더 표기). 내림으로 계산한다 — 올림하면 9분 40초가 "10분"이 되어
 * 집계에서 빠진 세션(§3.6d 10분 미만)이 기준을 채운 것처럼 보인다.
 * 1시간 미만이면 "MM분"만, 0이면 "0분".
 */
export const fmtHM = (totalSeconds: number): string => {
  const mins = Math.floor(Math.max(0, totalSeconds) / 60);
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
};
