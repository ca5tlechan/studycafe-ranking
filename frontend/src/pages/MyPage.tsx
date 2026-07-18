import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Bar, BarChart, LabelList, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import {
  sessionApi,
  statsApi,
  type CurrentSession,
  type StatsCalendar,
  type StatsHourlyPattern,
  type StatsOverview,
  type StatsWeekdayPattern,
  type Weekday,
} from '../lib/api';
import { CAFE_FALLBACK, fmtHM, fmtTime, studyTodayISO } from '../lib/format';
import { useAuth } from '../lib/auth';

const WEEKDAY_KO: Record<Weekday, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
};

const pad2 = (n: number) => String(n).padStart(2, '0');
/** 'YYYY-MM-DD' 를 직접 만든다. toISOString() 은 UTC 로 바꿔 버려 날짜가 하루 밀 수 있다. */
const isoDate = (y: number, m: number, d: number) => `${y}-${pad2(m)}-${pad2(d)}`;

/** 월요일 시작 인덱스(0=월 … 6=일). getDay() 는 0=일 이라 그대로 쓰면 한 칸씩 밀린다. */
const mondayFirstIndex = (date: Date) => (date.getDay() + 6) % 7;

interface ChartDatum {
  label: string;
  seconds: number;
}

/** 서로 독립적으로 실패할 수 있는 영역. 하나가 죽어도 나머지는 보여준다. */
const SECTIONS = ['session', 'overview', 'weekday', 'hourly'] as const;
type Section = (typeof SECTIONS)[number];

/** 영역 단위 실패 안내 — 실패를 '기록 없음'처럼 보이게 두지 않는다. */
function SectionError({ onRetry }: { onRetry: () => void }) {
  return (
    <p className="chart-sub" role="alert">
      불러오지 못했어요.{' '}
      <button type="button" className="link-btn" onClick={onRetry}>다시 시도</button>
    </p>
  );
}

/** 값은 축 대신 툴팁·최댓값 라벨로 읽는다(모바일 폭에서 축 눈금이 겹친다). */
function ChartTooltip({ active, payload, label, unit }: {
  active?: boolean;
  payload?: { value: number }[];
  label?: string;
  unit: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tip">
      <b>{label}{unit}</b> · {fmtHM(payload[0].value)}
    </div>
  );
}

function StudyBarChart({ data, unit, height, tickFormatter }: {
  data: ChartDatum[];
  unit: string;
  height: number;
  /** 축에 표시할 눈금만 걸러낸다. data 의 label 자체를 비우면 툴팁까지 빈칸이 된다. */
  tickFormatter?: (label: string) => string;
}) {
  const max = Math.max(...data.map((d) => d.seconds));
  if (max <= 0) return <p className="chart-empty">아직 기록이 없어요.</p>;

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 20, right: 2, bottom: 0, left: 2 }}>
        <XAxis
          dataKey="label"
          tickLine={false}
          axisLine={false}
          interval={0}
          tickFormatter={tickFormatter}
          tick={{ fill: 'var(--muted)', fontSize: 11 }}
        />
        <Tooltip
          cursor={{ fill: 'var(--surface-2)' }}
          content={<ChartTooltip unit={unit} />}
        />
        {/* 진입 애니메이션은 rAF 기반이라, 프레임이 스로틀링되면 막대가 0 높이에서 멈춘 채
            "차트가 비어 보이는" 상태가 된다. 폰에서 얻는 것도 없어 끈다. */}
        <Bar
          dataKey="seconds"
          fill="var(--primary)"
          radius={[4, 4, 0, 0]}
          maxBarSize={28}
          isAnimationActive={false}
        >
          {/* 모든 막대에 숫자를 붙이면 읽기 어려워진다 — 최댓값 하나만 직접 라벨. */}
          <LabelList
            dataKey="seconds"
            position="top"
            fill="var(--ink-2)"
            fontSize={11}
            fontWeight={700}
            formatter={(v) => (Number(v) === max ? fmtHM(Number(v)) : '')}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export default function MyPage() {
  const { user } = useAuth();
  const today = studyTodayISO();
  const [todayY, todayM] = [Number(today.slice(0, 4)), Number(today.slice(5, 7))];

  const [cursor, setCursor] = useState({ year: todayY, month: todayM });
  const [session, setSession] = useState<CurrentSession | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [calendar, setCalendar] = useState<StatsCalendar | null>(null);
  const [weekday, setWeekday] = useState<StatsWeekdayPattern | null>(null);
  const [hourly, setHourly] = useState<StatsHourlyPattern | null>(null);
  const [loading, setLoading] = useState(true);
  const [failedSections, setFailedSections] = useState<Set<Section>>(new Set());
  const [calFailed, setCalFailed] = useState(false);
  const calSeq = useRef(0);
  const loadedOnceRef = useRef(false);

  /**
   * 달과 무관한 데이터 — 한 번만 받는다. 서로 독립적인 조회이므로 병렬로 보내되,
   * Promise.all 은 쓰지 않는다. 하나만 실패해도 나머지 성공 결과까지 버려져
   * 멀쩡한 요약·기록이 화면에서 사라진다.
   */
  const loadStatic = useCallback(async () => {
    // 전체 로딩 화면은 첫 진입에서만. 섹션 '다시 시도'로 이걸 켜면 멀쩡히 보이던
    // 요약·캘린더·차트가 통째로 사라졌다가 돌아온다.
    if (!loadedOnceRef.current) setLoading(true);
    const [s, o, w, h] = await Promise.allSettled([
      sessionApi.current(),
      statsApi.overview(),
      statsApi.weekdayPattern(),
      statsApi.hourlyPattern(),
    ]);
    const failed = new Set<Section>();
    if (s.status === 'fulfilled') setSession(s.value); else failed.add('session');
    if (o.status === 'fulfilled') setOverview(o.value); else failed.add('overview');
    if (w.status === 'fulfilled') setWeekday(w.value); else failed.add('weekday');
    if (h.status === 'fulfilled') setHourly(h.value); else failed.add('hourly');
    setFailedSections(failed);
    loadedOnceRef.current = true;
    setLoading(false);
  }, []);

  /**
   * 달을 바꿀 때는 캘린더만 다시 받는다. 페이지 전체를 로딩 상태로 되돌리면
   * 요약·차트까지 매번 다시 받게 되고(달과 무관한데도), 화면이 통째로 깜빡인다.
   */
  const loadCalendar = useCallback(async (year: number, month: number) => {
    const seq = calSeq.current + 1;
    calSeq.current = seq;
    setCalFailed(false);
    try {
      const c = await statsApi.calendar(year, month);
      if (seq !== calSeq.current) return; // 달을 연달아 넘기면 늦게 온 응답이 최신 달을 덮어쓴다
      setCalendar(c);
    } catch {
      if (seq === calSeq.current) setCalFailed(true);
    }
    // 로딩 여부는 calReady(응답한 달 == 보고 있는 달)로 파생된다 — 따로 상태를 두면 어긋날 수 있다.
  }, []);

  useEffect(() => {
    void loadStatic();
  }, [loadStatic]);

  useEffect(() => {
    void loadCalendar(cursor.year, cursor.month);
  }, [loadCalendar, cursor]);

  /**
   * 응답한 달이 지금 보고 있는 달과 같을 때만 값을 쓴다. 아니면 이전 달 데이터를 들고
   * 새 달 칸을 그리게 되어, 응답이 오기 전까지 한 달 전체가 '기록 없음'으로 보인다.
   */
  const calReady = Boolean(calendar && calendar.year === cursor.year && calendar.month === cursor.month);
  const secondsByDate = useMemo(
    () => (calReady ? new Map((calendar?.days ?? []).map((d) => [d.date, d.totalSeconds])) : new Map<string, number>()),
    [calReady, calendar],
  );

  const weekdayData = useMemo<ChartDatum[]>(
    () => (weekday?.pattern ?? []).map((p) => ({ label: WEEKDAY_KO[p.weekday], seconds: p.avgSeconds })),
    [weekday],
  );

  const hourlyData = useMemo<ChartDatum[]>(
    () => (hourly?.pattern ?? []).map((p) => ({ label: String(p.hour), seconds: p.totalSeconds })),
    [hourly],
  );

  const topWeekday = useMemo(() => {
    if (!weekdayData.length) return null;
    const best = weekdayData.reduce((a, b) => (b.seconds > a.seconds ? b : a));
    return best.seconds > 0 ? best : null;
  }, [weekdayData]);

  const cells = useMemo(() => {
    const first = new Date(cursor.year, cursor.month - 1, 1);
    const daysInMonth = new Date(cursor.year, cursor.month, 0).getDate();
    const blanks = Array.from({ length: mondayFirstIndex(first) }, () => null);
    const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);
    return [...blanks, ...days];
  }, [cursor]);

  // 전부 실패했을 때만 페이지 전체를 오류로 본다(섹션이 늘어도 자동으로 따라간다)
  const allFailed = SECTIONS.every((s) => failedSections.has(s));
  const isCurrentMonth = cursor.year === todayY && cursor.month === todayM;
  const shift = (delta: number) => {
    const d = new Date(cursor.year, cursor.month - 1 + delta, 1);
    setCursor({ year: d.getFullYear(), month: d.getMonth() + 1 });
  };

  return (
    <>
      <header className="topbar">
        <div>
          <div className="hi">내 공부 기록</div>
          <h1>마이</h1>
        </div>
      </header>

      <div className="app-body">
        {/* §3.6c — 04:00 자동 마감 경고/페널티 안내. 로딩·에러와 무관하게 항상 상단에 노출. */}
        {user && user.warningCount > 0 && (
          <div className={`warn-banner${user.penalized ? ' penalized' : ''}`} role="status">
            {user.penalized ? (
              <>
                <b>경고 {user.warningCount}회</b> — 자동 마감이 반복돼 이번 기간 랭킹에서 제외됐어요.
                {/* 경고는 스터디-월(04:00 기준) 단위로 리셋된다 — 1일 00:00~03:59 는 아직 전월이므로
                    "매달 1일"이 아니라 "매달 1일 새벽 4시"가 실제 경계다(§3.6c). */}
                매달 1일 새벽 4시에 초기화돼요.
              </>
            ) : (
              <>
                <b>경고 {user.warningCount}/{user.penaltyThreshold}</b> — 04:00 전에 체크아웃하거나
                이후 다시 체크인하면 경고가 쌓이지 않아요.
              </>
            )}
          </div>
        )}
        {loading ? (
          <div className="center-msg">불러오는 중…</div>
        ) : allFailed ? (
          // 전부 실패 = 서버/연결 문제. 이때만 페이지 전체를 오류로 덮는다.
          <div className="card stack">
            <div className="state-line">기록을 불러오지 못했어요.</div>
            <button className="btn" onClick={() => void loadStatic()}>다시 시도</button>
          </div>
        ) : (
          <>
            {failedSections.has('session') ? (
              <div className="card sub">
                <SectionError onRetry={() => void loadStatic()} />
              </div>
            ) : session?.active ? (
              <div className="card sub now-line">
                <span className="pill studying"><span className="dot live" />공부 중</span>
                <span>
                  <b>{session.cafeName ?? CAFE_FALLBACK}</b>에서{' '}
                  {session.checkInAt && <><span className="num">{fmtTime(session.checkInAt)}</span>부터</>}
                </span>
              </div>
            ) : null}

            {/* 숫자 두 개는 차트보다 그냥 크게 보여주는 게 낫다. 실제 시간 그대로(§5.1 — 랭킹 캡 미적용). */}
            {failedSections.has('overview') ? (
              <div className="card">
                <div className="lbl">이번 주 · 이번 달</div>
                <SectionError onRetry={() => void loadStatic()} />
              </div>
            ) : (
              <div className="stat-row">
                <div className="stat">
                  <div className="lbl">이번 주</div>
                  <div className="stat-v num">{fmtHM(overview?.weekSeconds ?? 0)}</div>
                </div>
                <div className="stat">
                  <div className="lbl">이번 달</div>
                  <div className="stat-v num">{fmtHM(overview?.monthSeconds ?? 0)}</div>
                </div>
              </div>
            )}

            <section className="card">
              <div className="cal-head">
                <button className="cal-nav" onClick={() => shift(-1)} aria-label="이전 달">‹</button>
                <b className="num">{cursor.year}년 {cursor.month}월</b>
                <button
                  className="cal-nav"
                  onClick={() => shift(1)}
                  disabled={isCurrentMonth}
                  aria-label="다음 달"
                >
                  ›
                </button>
              </div>
              {calFailed && (
                <p className="chart-sub">
                  이 달 기록을 불러오지 못했어요.{' '}
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => void loadCalendar(cursor.year, cursor.month)}
                  >
                    다시 시도
                  </button>
                </p>
              )}
              {/* 아직 이 달 응답이 오기 전엔 시간이 비어 있다. 흐리게 해서
                  '기록 없는 달'과 '아직 모르는 달'을 구분한다. */}
              <div className={`cal-grid${!calReady && !calFailed ? ' loading' : ''}`}>
                {['월', '화', '수', '목', '금', '토', '일'].map((d) => (
                  <div key={d} className="cal-dow">{d}</div>
                ))}
                {cells.map((day, i) => {
                  if (day === null) return <div key={`b${i}`} className="cal-cell empty" />;
                  const key = isoDate(cursor.year, cursor.month, day);
                  const secs = secondsByDate.get(key);
                  return (
                    <div key={key} className={`cal-cell${key === today ? ' today' : ''}${secs ? ' has' : ''}`}>
                      <span className="cal-d num">{day}</span>
                      {secs ? <span className="cal-t num">{fmtHM(secs)}</span> : null}
                    </div>
                  );
                })}
              </div>
            </section>

            <section className="card">
              <div className="lbl">요일별 평균</div>
              {failedSections.has('weekday') ? (
                <SectionError onRetry={() => void loadStatic()} />
              ) : (
                <>
                  {/* '이번 주'가 아니라 누적 평균이다(§5.1) — 오해하지 않도록 한 줄로 밝힌다. */}
                  <p className="chart-sub">
                    {topWeekday
                      ? <>그동안 <b>{topWeekday.label}요일</b>에 가장 많이 공부했어요.</>
                      : '기록이 쌓이면 요일별 패턴이 보여요.'}
                  </p>
                  <StudyBarChart data={weekdayData} unit="요일" height={170} />
                </>
              )}
            </section>

            <section className="card">
              <div className="lbl">시간대별 공부량</div>
              {failedSections.has('hourly') ? (
                <SectionError onRetry={() => void loadStatic()} />
              ) : (
                <>
                  <p className="chart-sub">하루 중 언제 공부하는지 — 벽시계 기준이에요.</p>
                  <StudyBarChart
                    data={hourlyData}
                    unit="시"
                    height={170}
                    // 24개를 다 찍으면 눈금이 겹친다 — 6시간 간격만 표시.
                    tickFormatter={(h) => (Number(h) % 6 === 0 ? h : '')}
                  />
                </>
              )}
            </section>
          </>
        )}
      </div>
    </>
  );
}
