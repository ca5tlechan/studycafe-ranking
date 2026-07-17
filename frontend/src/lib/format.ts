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
