import { useCallback, useEffect, useRef, useState } from 'react';
import PeriodFilter from '../components/PeriodFilter';
import RankingBoard from '../components/RankingBoard';
import { rankingApi, type RankingPeriod, type SchoolMine } from '../lib/api';

/** §5.3 — 같은 학교 안에서의 개인 랭킹. 구성은 개인 랭킹과 같고 대상만 내 학교로 좁힌다. */
export default function SchoolPage() {
  const [period, setPeriod] = useState<RankingPeriod>('this_week');
  const [data, setData] = useState<SchoolMine | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const seq = useRef(0);

  const load = useCallback(async (p: RankingPeriod) => {
    const mine = seq.current + 1;
    seq.current = mine;
    setLoading(true);
    setFailed(false);
    try {
      const res = await rankingApi.schoolMine(p);
      if (mine !== seq.current) return; // 기간을 연달아 바꾸면 늦게 온 응답이 최신 것을 덮는다
      setData(res);
    } catch {
      if (mine === seq.current) setFailed(true);
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(period);
  }, [load, period]);

  // 무소속이면 ranking 이 없으므로 기간과의 대조는 랭킹이 있을 때만 의미가 있다.
  const fresh = data ? !data.available || data.ranking?.period === period : false;

  return (
    <>
      <header className="topbar">
        <div>
          <div className="hi">{data?.available ? data.schoolName : '학교 친구들과 함께'}</div>
          <h1>우리 학교</h1>
        </div>
      </header>

      <div className="app-body">
        {/* 무소속이면 기간을 바꿔도 볼 게 없다 — 필터를 띄우지 않는다. */}
        {data?.available !== false && <PeriodFilter value={period} onChange={setPeriod} />}

        <section className="card">
          {failed ? (
            <div className="stack">
              <div className="state-line">랭킹을 불러오지 못했어요.</div>
              <button className="btn" onClick={() => void load(period)}>다시 시도</button>
            </div>
          ) : loading || !fresh ? (
            <div className="center-msg">불러오는 중…</div>
          ) : !data?.available ? (
            // §5.3 — 무소속 안내
            <p className="chart-empty">학교를 설정하면 우리 학교 랭킹을 볼 수 있어요.</p>
          ) : (
            data.ranking && <RankingBoard data={data.ranking} />
          )}
        </section>
      </div>
    </>
  );
}
