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
  const today = studyTodayISO();
  const [todayY, todayM] = [Number(today.slice(0, 4)), Number(today.slice(5, 7))];

  const [cursor, setCursor] = useState({ year: todayY, month: todayM });
  const [session, setSession] = useState<CurrentSession | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [calendar, setCalendar] = useState<StatsCalendar | null>(null);
  const [weekday, setWeekday] = useState<StatsWeekdayPattern | null>(null);
  const [hourly, setHourly] = useState<StatsHourlyPattern | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [calFailed, setCalFailed] = useState(false);
  const calSeq = useRef(0);

  /** 달과 무관한 데이터 — 한 번만 받는다. */
  const loadStatic = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      // 서로 독립적인 조회다 — 순차로 기다릴 이유가 없다.
      const [s, o, w, h] = await Promise.all([
        sessionApi.current(),
        statsApi.overview(),
        statsApi.weekdayPattern(),
        statsApi.hourlyPattern(),
      ]);
      setSession(s);
      setOverview(o);
      setWeekday(w);
      setHourly(h);
    } catch {
      setFailed(true); // 실패를 '기록 없음'으로 숨기지 않는다
    } finally {
      setLoading(false);
    }
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
  }, []);

  useEffect(() => {
    void loadStatic();
  }, [loadStatic]);

  useEffect(() => {
    void loadCalendar(cursor.year, cursor.month);
  }, [loadCalendar, cursor]);

  const secondsByDate = useMemo(
    () => new Map((calendar?.days ?? []).map((d) => [d.date, d.totalSeconds])),
    [calendar],
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
        {loading ? (
          <div className="center-msg">불러오는 중…</div>
        ) : failed ? (
          <div className="card stack">
            <div className="state-line">기록을 불러오지 못했어요.</div>
            <button className="btn" onClick={() => void loadStatic()}>다시 시도</button>
          </div>
        ) : (
          <>
            {session?.active && (
              <div className="card sub now-line">
                <span className="pill studying"><span className="dot live" />공부 중</span>
                <span>
                  <b>{session.cafeName ?? CAFE_FALLBACK}</b>에서{' '}
                  {session.checkInAt && <><span className="num">{fmtTime(session.checkInAt)}</span>부터</>}
                </span>
              </div>
            )}

            {/* 숫자 두 개는 차트보다 그냥 크게 보여주는 게 낫다. 실제 시간 그대로(§5.1 — 랭킹 캡 미적용). */}
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
              <div className="cal-grid">
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
              {/* '이번 주'가 아니라 누적 평균이다(§5.1) — 오해하지 않도록 한 줄로 밝힌다. */}
              <p className="chart-sub">
                {topWeekday
                  ? <>그동안 <b>{topWeekday.label}요일</b>에 가장 많이 공부했어요.</>
                  : '기록이 쌓이면 요일별 패턴이 보여요.'}
              </p>
              <StudyBarChart data={weekdayData} unit="요일" height={170} />
            </section>

            <section className="card">
              <div className="lbl">시간대별 공부량</div>
              <p className="chart-sub">하루 중 언제 공부하는지 — 벽시계 기준이에요.</p>
              <StudyBarChart
                data={hourlyData}
                unit="시"
                height={170}
                // 24개를 다 찍으면 눈금이 겹친다 — 6시간 간격만 표시(값은 툴팁으로 읽는다).
                tickFormatter={(h) => (Number(h) % 6 === 0 ? h : '')}
              />
            </section>
          </>
        )}
      </div>
    </>
  );
}
