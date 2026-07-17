import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import { ApiError, sessionApi, type CurrentSession, type SessionToggle } from '../lib/api';

const SCANNER_ID = 'qr-reader';

/** §3.6d — 10분 미만 세션은 집계에서 제외된다. 판정은 세션 전체 길이 기준. */
const MIN_SESSION_SECONDS = 10 * 60;

/** cafeName 은 타입상 null 이 가능하다. 빈칸으로 새면 "지금 에서 공부 중"이 된다. */
const CAFE_FALLBACK = '현재 카페';

type CameraState = 'starting' | 'running' | 'unavailable';

const fmtTime = (iso: string): string =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

const secondsBetween = (fromIso: string, toIso: string): number =>
  Math.max(0, Math.floor((new Date(toIso).getTime() - new Date(fromIso).getTime()) / 1000));

/**
 * 내림으로 표시한다. 올림하면 9분 40초가 "10분"이 되어, 집계에서 빠졌는데도
 * 기준(10분)을 채운 것처럼 보인다.
 */
const fmtDuration = (secs: number): string => {
  if (secs < 60) return `${secs}초`;
  const mins = Math.floor(secs / 60);
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
};

const UNCERTAIN_MESSAGE =
  '기록이 반영됐는지 확인하지 못했어요. 아래 현재 상태를 확인하고, 필요하면 다시 스캔해 주세요.';

/**
 * 토글은 상태를 바꾸는 요청이라, 실패를 "반영 안 됨"으로 단정하면 안 된다.
 * - 4xx: 서버가 요청을 거절했으므로 상태가 바뀌지 않았다 → 곧바로 재스캔해도 안전하다.
 * - 5xx·네트워크: 서버가 이미 반영하고 응답만 유실됐을 수 있다 → 재스캔하면 반대로 토글된다.
 */
const isRejected = (err: unknown): boolean => err instanceof ApiError && err.status < 500;

/** 토글 결과. applied=반영됨, rejected=반영 안 됨(재시도 안전), uncertain=반영 여부 모름 */
type SubmitOutcome = 'applied' | 'rejected' | 'uncertain' | 'busy';

/**
 * 서버 메시지를 그대로 노출하지 않는다. 예컨대 잘못된 QR의 404 메시지에는 스캔한 토큰 값이
 * 그대로 담겨 오므로, 우리가 예상한 상태만 문구로 매핑하고 나머지는 일반 문구로 통일한다.
 */
function messageFor(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 404) return '이 카페의 QR이 아니에요. 카페에 부착된 QR을 찍어 주세요.';
    if (err.status === 409) return '이미 체크인돼 있어요. 잠시 후 다시 시도해 주세요.';
  }
  return '기록에 실패했어요. 다시 시도해 주세요.';
}

export default function CheckInPage() {
  const [camera, setCamera] = useState<CameraState>('starting');
  const [current, setCurrent] = useState<CurrentSession | null>(null);
  const [statusFailed, setStatusFailed] = useState(false);
  const [result, setResult] = useState<SessionToggle | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [manual, setManual] = useState('');
  const [uncertain, setUncertain] = useState(false); // 반영 여부를 모르는 실패 → 자동 재스캔 금지
  const [scanNonce, setScanNonce] = useState(0); // 값이 바뀌면 스캐너를 다시 켠다

  const inFlightRef = useRef(false);
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const handledRef = useRef(false);

  const loadStatus = useCallback(async () => {
    setStatusFailed(false);
    try {
      setCurrent(await sessionApi.current());
    } catch {
      setStatusFailed(true);
    }
  }, []);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  /**
   * 넘겨받은 인스턴스만 정지한다. ref 를 다시 읽으면, 권한 승인이 늦어지는 사이 이전 effect 의
   * 뒷정리가 이미 교체된 새 스캐너를 멈춰 버린다(= 이전 카메라는 켜진 채로 남는다).
   */
  const stopScanner = useCallback(async (scanner: Html5Qrcode) => {
    try {
      if (scanner.isScanning) await scanner.stop();
      scanner.clear();
    } catch {
      /* 이미 정지·해제된 경우 — 무시해도 되는 상태다 */
    }
    if (scannerRef.current === scanner) scannerRef.current = null;
  }, []);

  const stopCurrent = useCallback(async () => {
    const scanner = scannerRef.current;
    if (scanner) await stopScanner(scanner);
  }, [stopScanner]);

  /**
   * 토글 요청의 단일 진입점. 수동 실행과 카메라 콜백이 같은 in-flight 잠금을 공유해야 한다.
   * (수동 요청 중 QR이 인식되면 토글이 연속 전환돼 버린다.)
   */
  const submit = useCallback(async (cafeToken: string): Promise<SubmitOutcome> => {
    if (inFlightRef.current) return 'busy';
    inFlightRef.current = true;
    setBusy(true);
    setError('');
    try {
      setResult(await sessionApi.toggle(cafeToken));
      return 'applied';
    } catch (err) {
      const rejected = isRejected(err);
      setError(rejected ? messageFor(err) : UNCERTAIN_MESSAGE);
      return rejected ? 'rejected' : 'uncertain';
    } finally {
      inFlightRef.current = false;
      setBusy(false);
    }
  }, []);

  /** 반영 여부를 모르는 실패 — 자동 재스캔을 막고 서버의 실제 상태를 다시 물어 보여준다. */
  const holdForReconcile = useCallback(async () => {
    await stopCurrent();
    setUncertain(true);
    await loadStatus();
  }, [stopCurrent, loadStatus]);

  const handleScan = useCallback(
    async (text: string) => {
      if (handledRef.current) return; // 초당 여러 번 디코딩되므로 첫 인식만 처리
      handledRef.current = true;
      const outcome = await submit(text);
      if (outcome === 'applied') await stopCurrent();
      else if (outcome === 'uncertain') await holdForReconcile();
      else handledRef.current = false; // 서버가 거절했거나 다른 요청 진행 중 → 재스캔 안전
    },
    [submit, stopCurrent, holdForReconcile],
  );

  useEffect(() => {
    // 스캐너를 감추는 분기(uncertain 등)에서는 대상 엘리먼트가 없다. 생성자가 던지므로 먼저 막는다.
    if (!document.getElementById(SCANNER_ID)) return;

    let cancelled = false;
    const scanner = new Html5Qrcode(SCANNER_ID, {
      formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
      verbose: false,
    });
    scannerRef.current = scanner;

    scanner
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 232, height: 232 } },
        (text) => void handleScan(text),
        undefined,
      )
      .then(() => {
        // StrictMode 이중 마운트/이탈 시 켜 둔 카메라가 남지 않도록
        if (cancelled) void stopScanner(scanner);
        else setCamera('running');
      })
      .catch(() => {
        if (!cancelled) setCamera('unavailable');
      });

    return () => {
      cancelled = true;
      void stopScanner(scanner);
    };
    // scanNonce 가 바뀌면(= 사용자가 다시 스캔을 누르면) 스캐너를 새로 켠다.
  }, [handleScan, stopScanner, scanNonce]);

  const runManual = async () => {
    const outcome = await submit(manual.trim());
    if (outcome === 'applied') await stopCurrent();
    else if (outcome === 'uncertain') await holdForReconcile();
  };

  /** 반영 여부 확인 후 사용자가 명시적으로 다시 스캔할 때만 카메라를 재개한다. */
  const rescan = () => {
    handledRef.current = false;
    setUncertain(false);
    setError('');
    setCamera('starting');
    setScanNonce((n) => n + 1);
  };

  const checkedOutSeconds =
    result?.action === 'CHECK_OUT' && result.checkOutAt
      ? secondsBetween(result.checkInAt, result.checkOutAt)
      : null;

  return (
    <>
      <header className="topbar">
        <div>
          <div className="hi">카페 QR을 찍으면 기록돼요</div>
          <h1>체크인</h1>
        </div>
        <Link className="btn ghost" style={{ padding: '8px 13px', fontSize: 13 }} to="/">
          닫기
        </Link>
      </header>

      <div className="app-body">
        {error && <div className="banner" role="alert">{error}</div>}

        {result ? (
          <div className="card stack">
            <span className={`pill ${result.action === 'CHECK_IN' ? 'studying' : 'idle'}`}>
              <span className="dot" />
              {result.action === 'CHECK_IN' ? '체크인 완료' : '체크아웃 완료'}
            </span>
            {result.action === 'CHECK_IN' ? (
              <div className="result-line">
                <span className="num">{fmtTime(result.checkInAt)}</span>부터 기록을 시작했어요.
              </div>
            ) : (
              <div className="result-line">
                <span className="num">{fmtTime(result.checkInAt)}</span> ~{' '}
                <span className="num">{result.checkOutAt ? fmtTime(result.checkOutAt) : '-'}</span> ·{' '}
                <b>{checkedOutSeconds !== null ? fmtDuration(checkedOutSeconds) : '-'}</b> 공부했어요.
              </div>
            )}
            {checkedOutSeconds !== null && checkedOutSeconds < MIN_SESSION_SECONDS && (
              <p className="hint-txt">
                {MIN_SESSION_SECONDS / 60}분 미만 세션은 기록에 반영되지 않아요.
              </p>
            )}
            <Link className="btn full" to="/">홈으로</Link>
          </div>
        ) : (
          <div className="card stack">
            {statusFailed ? (
              <div className="scan-hint">
                지금 상태를 불러오지 못했어요. QR을 찍으면 체크인/체크아웃이 전환돼요.{' '}
                <button type="button" className="link-btn" onClick={() => void loadStatus()}>
                  다시 시도
                </button>
              </div>
            ) : uncertain ? (
              // 반영 여부를 모르는 실패 뒤 — 서버에 실제로 뭐가 남았는지를 보여준다.
              <div className="scan-hint">
                {current?.active ? (
                  <>
                    서버에는 <b>{current.cafeName ?? CAFE_FALLBACK}</b>에서 <b>공부 중</b>으로 기록돼 있어요.
                  </>
                ) : (
                  <>서버에는 진행 중인 공부 기록이 <b>없어요</b>.</>
                )}
              </div>
            ) : (
              <div className="scan-hint">
                {current?.active ? (
                  <>
                    지금 <b>{current.cafeName ?? CAFE_FALLBACK}</b>에서 공부 중이에요. QR을 찍으면 <b>체크아웃</b>돼요.
                  </>
                ) : (
                  <>QR을 찍으면 <b>체크인</b>돼요.</>
                )}
              </div>
            )}

            {uncertain ? (
              // 카메라를 다시 켜지 않는다 — QR이 화면에 남아 있어 곧바로 재스캔되면 반대로 토글된다.
              <button className="btn" onClick={rescan}>다시 스캔</button>
            ) : (
              <div className={`scanner${camera === 'running' ? ' on' : ''}`}>
                <div id={SCANNER_ID} />
                {camera === 'starting' && (
                  <div className="scanner-msg">
                    <div className="spinner" aria-label="카메라 준비 중" />
                  </div>
                )}
                {camera === 'unavailable' && (
                  <div className="scanner-msg">
                    카메라를 열 수 없어요.
                    <br />
                    브라우저에서 카메라 권한을 허용했는지 확인해 주세요.
                  </div>
                )}
              </div>
            )}

            {busy && <div className="scan-hint">기록하는 중…</div>}
          </div>
        )}

        {/* §3.6a — 04:00 마감/재체크인 안내는 체크인 화면에 상시 노출한다. */}
        <p className="notice">
          하루는 <b>새벽 4시</b>에 마감돼요. 계속 공부하려면 04:00 이후 다시 체크인해 주세요.
          체크아웃 없이 04:00을 넘기면 자동 종료되고 경고가 쌓여요.
        </p>

        {/* §2 [예외] 개발 빌드 한정 수동 입력 — 프로덕션 빌드에서는 이 분기가 통째로 제거된다.
            프로덕션에서 토글을 발생시키는 경로는 카메라 QR 스캔뿐이어야 한다. */}
        {import.meta.env.DEV && !result && (
          <div className="card sub stack">
            <span className="lbl">개발용 수동 입력</span>
            <p className="hint-txt">
              카메라가 없는 데스크톱에서 흐름을 검증하기 위한 입력이에요. 프로덕션 빌드에는 포함되지 않아요.
            </p>
            <input
              className="input"
              value={manual}
              onChange={(e) => setManual(e.target.value)}
              placeholder="카페 QR 토큰"
              aria-label="카페 QR 토큰"
            />
            <button className="btn" disabled={busy || !manual.trim()} onClick={() => void runManual()}>
              토글 실행
            </button>
          </div>
        )}
      </div>
    </>
  );
}
