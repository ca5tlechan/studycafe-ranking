import { useEffect } from 'react';
import { useRouteError } from 'react-router-dom';
import { isChunkLoadError, reloadForStaleChunk } from '../lib/staleChunk';

/**
 * 라우트 렌더 중 발생한 에러의 폴백 화면(라우터 기본 "Unexpected Application Error!" 대체).
 * 배포로 인한 낡은 청크 에러면 자동 새로고침을 한 번 더 시도하고(가드로 루프 방지), 안내를 보여준다.
 */
export default function RouteError() {
  const err = useRouteError();
  const stale = isChunkLoadError(err);

  useEffect(() => {
    if (stale) reloadForStaleChunk(); // 낡은 청크 → 새 버전으로 새로고침
  }, [stale]);

  return (
    <div className="error-screen" role="alert">
      <div className="error-icon" aria-hidden="true">{stale ? '🔄' : '⚠️'}</div>
      <h2>{stale ? '새 버전으로 업데이트할게요' : '문제가 생겼어요'}</h2>
      <p>
        {stale
          ? '잠시 후 자동으로 새로고침돼요. 화면이 그대로면 아래 버튼을 눌러 주세요.'
          : '화면을 불러오지 못했어요. 새로고침하면 대개 해결돼요.'}
      </p>
      <button className="btn" onClick={() => window.location.reload()}>새로고침</button>
    </div>
  );
}
