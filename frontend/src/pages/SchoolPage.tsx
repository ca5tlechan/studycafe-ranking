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
    } catch (e) {
      console.error('우리 학교 랭킹 로드 실패', e); // 실패 원인 추적용
      if (mine === seq.current) setFailed(true);
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(period);
  }, [load, period]);

  // 응답이 지금 기간의 것인지. 무소속(available=false)이거나 랭킹이 아예 없으면(ranking=null)
  // 기간 대조는 의미가 없으니 fresh 로 본다. 이 방어가 없으면 ranking=null 일 때 period 불일치가
  // 되어 loading=false 인데도 '불러오는 중'에 영구히 갇힌다.
  const fresh = data ? !data.available || !data.ranking || data.ranking.period === period : false;

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
          ) : data.ranking ? (
            <RankingBoard data={data.ranking} />
          ) : (
            // 소속은 있으나 랭킹 데이터가 없는 경우(방어) — 무한 로딩 대신 빈 상태를 보여준다.
            <p className="chart-empty">이 기간에는 아직 기록이 없어요.</p>
          )}
        </section>
      </div>
    </>
  );
}
