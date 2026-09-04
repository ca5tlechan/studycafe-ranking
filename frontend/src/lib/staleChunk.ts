// 배포로 지연 로딩 청크(코드 분할 조각)의 파일 이름이 새 해시로 바뀌면, 배포 전에 열어둔 앱은
// 옛 이름의 청크를 받으려다 404 로 실패한다("Importing a module script failed"). 이때 사용자에게
// 깨진 에러 화면을 보이는 대신, 새 버전을 받도록 한 번만 자동 새로고침한다.

const RELOAD_KEY = 'scr_stale_reloaded_at';
const GUARD_MS = 10_000; // 이 시간 내 이미 리로드했으면 다시 안 함(무한 새로고침 루프 방지)

/** 낡은 청크로 판단되는 에러인지. (React.lazy / dynamic import 실패 메시지 패턴) */
export function isChunkLoadError(err: unknown): boolean {
  const msg = err instanceof Error ? err.message : String(err ?? '');
  return /importing a module script failed|dynamically imported module|failed to fetch dynamically imported|loading chunk|chunkloaderror/i.test(
    msg,
  );
}

/** 낡은 청크 상황에서 한 번만 새로고침한다(가드로 루프 방지). 저장소 거부 시엔 그냥 새로고침. */
export function reloadForStaleChunk(): void {
  try {
    const last = Number(sessionStorage.getItem(RELOAD_KEY) || '0');
    if (Date.now() - last < GUARD_MS) return; // 방금 리로드했는데 또 실패 → 멈춘다(에러 화면으로 폴백)
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
  } catch {
    /* 저장소 차단(프라이빗 모드 등)이면 가드를 보장할 수 없다 → 자동 새로고침을 건너뛰고
       에러 폴백(RouteError)의 수동 새로고침 버튼에 맡긴다. 무한 새로고침 루프 방지. */
    return;
  }
  window.location.reload();
}

/**
 * Vite 는 dynamic import(청크) 로드 실패 때 window 에 `vite:preloadError` 를 쏜다(프로덕션 빌드 한정).
 * 이를 잡아 자동 새로고침 → 사용자는 라우터 기본 에러 화면 대신 잠깐의 리로드만 겪는다.
 */
export function installPreloadErrorReload(): void {
  window.addEventListener('vite:preloadError', () => reloadForStaleChunk());
}
