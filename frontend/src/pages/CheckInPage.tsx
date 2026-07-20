import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Html5Qrcode, Html5QrcodeScannerState, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import { ApiError, sessionApi, type CurrentSession, type SessionToggle } from '../lib/api';
import { CAFE_FALLBACK, fmtTime } from '../lib/format';

const SCANNER_ID = 'qr-reader';

/** §3.6d — 10분 미만 세션은 집계에서 제외된다. 판정은 세션 전체 길이 기준. */
const MIN_SESSION_SECONDS = 10 * 60;


type CameraState = 'idle' | 'starting' | 'running' | 'unavailable';


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
type SubmitOutcome = 'applied' | 'rejected' | 'uncertain';

/**
 * 서버 메시지를 그대로 노출하지 않는다. 예컨대 잘못된 QR의 404 메시지에는 스캔한 토큰 값이
 * 그대로 담겨 오므로, 우리가 예상한 상태만 문구로 매핑하고 나머지는 일반 문구로 통일한다.
 */
function messageFor(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 404) return '이 카페의 QR이 아니에요. 카페에 부착된 QR을 찍어 주세요.';
    // 409 = 다른 요청이 이미 체크인을 만든 상태. 여기서 "다시 시도"를 권하면 그 다음 토글이
    // 체크아웃이 되어 버리므로, 재시도가 아니라 상태 확인으로 안내한다.
    if (err.status === 409) return '이미 체크인돼 있어요. 홈에서 현재 상태를 확인해 주세요.';
  }
  return '기록에 실패했어요. 다시 시도해 주세요.';
}

export default function CheckInPage() {
  const [camera, setCamera] = useState<CameraState>('idle');
  const [current, setCurrent] = useState<CurrentSession | null>(null);
  const [statusFailed, setStatusFailed] = useState(false);
  const [result, setResult] = useState<SessionToggle | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [manual, setManual] = useState('');
  const [uncertain, setUncertain] = useState(false); // 반영 여부를 모르는 실패 → 자동 재스캔 금지
  const [reconciling, setReconciling] = useState(false); // 서버 상태 재조회 중 → 아직 결론 아님
  const [scanNonce, setScanNonce] = useState(0); // 값이 바뀌면 스캐너를 다시 켠다

  const rejectedTokenRef = useRef<string | null>(null); // 방금 거절당한 토큰 — 자동 재요청 방지
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
   *
   * 공개 필드 isScanning 으로 막으면 안 된다. 그 값은 video 의 'playing' 이벤트에서야 켜지는데,
   * start() 프로미스는 그보다 먼저 resolve 된다. 그래서 정지 시점엔 대개 아직 false 이고,
   * stop() 을 건너뛰면 카메라 트랙이 살아남는다. 라이브러리의 stop() 이 실제로 보는 값은 getState().
   * stop() 은 트랙 정지(= 카메라 꺼짐)와 video 엘리먼트 제거까지 해주므로 clear() 는 부르지 않는다
   * (clear() 는 elementId 로 DOM 을 찾아 비우기 때문에 새 스캐너의 화면을 지울 수 있다).
   */
  const stopScanner = useCallback(async (scanner: Html5Qrcode) => {
    try {
      if (scanner.getState() !== Html5QrcodeScannerState.NOT_STARTED) await scanner.stop();
    } catch {
      /* 이미 정지된 경우 — 무시해도 되는 상태다 */
    }
    if (scannerRef.current === scanner) scannerRef.current = null;
  }, []);

  const stopCurrent = useCallback(async () => {
    const scanner = scannerRef.current;
    if (scanner) await stopScanner(scanner);
  }, [stopScanner]);

  /** 토글 요청 자체. 잠금은 호출자(runToggle)가 쥔다. */
  const submit = useCallback(async (cafeToken: string): Promise<SubmitOutcome> => {
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
      setBusy(false);
    }
  }, []);

  /**
   * 반영 여부를 모르는 실패 — 자동 재스캔을 막고 서버의 실제 상태를 다시 물어 보여준다.
   * 재조회가 끝나기 전에는 화면의 상태가 아직 옛것이므로, reconciling 으로 구분해 조작을 막는다.
   */
  const holdForReconcile = useCallback(async () => {
    await stopCurrent();
    setUncertain(true);
    setReconciling(true);
    try {
      await loadStatus();
    } finally {
      setReconciling(false);
    }
  }, [stopCurrent, loadStatus]);

  /**
   * 토글의 단일 진입점 — 카메라 콜백과 수동 실행이 이 잠금 하나를 공유한다.
   * handledRef 는 await 이전에 동기적으로 선점하므로 두 경로가 겹쳐 두 번 토글될 수 없다.
   * (예전엔 수동 실행이 이 잠금을 안 쥐어서, 수동 토글 직후 도착한 QR 인식이 곧바로 반대로
   *  토글해 버릴 수 있었다.)
   * 성공·미상일 때는 잠금을 풀지 않는다. 결과 화면이나 재조회 화면으로 넘어가기 때문이다.
   */
  const runToggle = useCallback(
    async (token: string) => {
      if (handledRef.current) return;
      handledRef.current = true;
      const outcome = await submit(token);
      if (outcome === 'applied') {
        rejectedTokenRef.current = null;
        await stopCurrent();
      } else if (outcome === 'uncertain') {
        await holdForReconcile();
      } else {
        rejectedTokenRef.current = token; // 같은 토큰 자동 재요청 방지
        handledRef.current = false; // 서버가 거절 → 상태 불변 → 다른 QR 은 다시 시도해도 안전
      }
    },
    [submit, stopCurrent, holdForReconcile],
  );

  const handleScan = useCallback(
    async (text: string) => {
      // 거절된 QR 이 화면에 남아 있으면 초당 10번 다시 인식된다. 재스캔 자체는 안전하지만
      // 같은 토큰을 무한히 재요청하게 되므로, 방금 거절당한 토큰은 보내지 않는다.
      // (사용자가 올바른 QR 로 옮기면 토큰이 달라지므로 그때 정상 처리된다.)
      if (text === rejectedTokenRef.current) return;
      await runToggle(text);
    },
    [runToggle],
  );

  useEffect(() => {
    // 초기(scanNonce 0)엔 카메라를 자동으로 켜지 않는다 — iOS(특히 설치형 PWA)는 사용자 탭(제스처)
    // 없이는 카메라 권한 프롬프트를 띄우지 않아 getUserMedia 가 조용히 막힌다. "QR 스캔 시작"을
    // 누르면 rescan 이 scanNonce 를 올려 여기서 카메라를 켠다.
    if (scanNonce === 0) return;
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

  const runManual = () => void runToggle(manual.trim());

  /** 사용자가 명시적으로 요청할 때만 카메라를 다시 켠다(반영 여부 확인 후, 또는 카메라 실패 후). */
  const rescan = () => {
    handledRef.current = false;
    rejectedTokenRef.current = null;
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
            {/* uncertain 을 먼저 본다. 토글이 5xx 로 실패하면 뒤이은 상태 재조회도 같은 이유로
                실패하기 쉬운데, statusFailed 를 먼저 보면 "QR을 찍으면 전환돼요"라는 반대 안내가
                뜬다(카메라는 이미 꺼져 있고, 재스캔은 이중 토글을 부른다). */}
            {uncertain ? (
              // 반영 여부를 모르는 실패 뒤 — 서버에 실제로 뭐가 남았는지를 보여준다.
              <div className="scan-hint">
                {reconciling ? (
                  // 아직 재조회 중 — current 는 토글 이전의 옛 상태다. 결론인 것처럼 보이면 안 된다.
                  <>서버에 실제로 기록됐는지 확인하는 중이에요…</>
                ) : statusFailed ? (
                  <>서버 상태도 확인하지 못했어요. 연결이 돌아온 뒤 홈에서 기록을 확인해 주세요.</>
                ) : current?.active ? (
                  <>
                    서버에는 <b>{current.cafeName ?? CAFE_FALLBACK}</b>에서 <b>공부 중</b>으로 기록돼 있어요.
                  </>
                ) : (
                  <>서버에는 진행 중인 공부 기록이 <b>없어요</b>.</>
                )}
              </div>
            ) : statusFailed ? (
              <div className="scan-hint">
                지금 상태를 불러오지 못했어요. QR을 찍으면 체크인/체크아웃이 전환돼요.{' '}
                <button type="button" className="link-btn" onClick={() => void loadStatus()}>
                  다시 시도
                </button>
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
              // 재조회가 끝나기 전엔 뭐가 기록됐는지 모르므로 그동안은 다시 스캔도 막는다.
              <button className="btn" disabled={reconciling} onClick={rescan}>
                {reconciling ? '확인하는 중…' : '다시 스캔'}
              </button>
            ) : camera === 'idle' ? (
              // iOS 는 사용자 탭(제스처)이 있어야 카메라를 연다 — 자동 실행 대신 버튼으로 시작한다.
              <button className="btn full" onClick={rescan}>QR 스캔 시작</button>
            ) : (
              <div className="scanner">
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
                    {/* 보안 컨텍스트(HTTPS/localhost)가 아니면 권한과 무관하게 카메라 자체를 못 연다.
                        폰에서 http://192.168.x.x 로 접속한 경우가 여기 해당한다. */}
                    {window.isSecureContext
                      ? '브라우저에서 카메라 권한을 허용했는지 확인해 주세요.'
                      : 'HTTPS 연결에서만 카메라를 쓸 수 있어요.'}
                    <br />
                    {/* 이 상태에서 스캐너를 다시 만들 방법이 없으면 화면이 막힌다(프로덕션엔 수동 입력이 없다). */}
                    <button type="button" className="btn ghost" onClick={rescan}>다시 시도</button>
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
            {/* uncertain 이면 잠금(handledRef)이 잡혀 있어 눌러도 아무 일이 없다 → 아예 막는다.
                재개는 "다시 스캔"으로만 한다(=서버 상태를 확인한 뒤). */}
            <button
              className="btn"
              disabled={busy || uncertain || reconciling || !manual.trim()}
              onClick={runManual}
            >
              토글 실행
            </button>
          </div>
        )}
      </div>
    </>
  );
}
