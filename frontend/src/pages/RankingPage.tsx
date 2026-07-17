import { useCallback, useEffect, useRef, useState } from 'react';
import PeriodFilter from '../components/PeriodFilter';
import RankingBoard from '../components/RankingBoard';
import {
  rankingApi,
  type IndividualRanking,
  type RankingPeriod,
  type SchoolEntry,
  type SchoolRanking,
} from '../lib/api';
import { fmtHM } from '../lib/format';

type Tab = 'individual' | 'school';

/** 포디움 표시 순서: 2·1·3 (가운데가 1등). */
const PODIUM_ORDER = [2, 1, 3];

function SchoolPodium({ entries }: { entries: SchoolEntry[] }) {
  const byRank = new Map(entries.map((e) => [e.rank, e]));
  const shown = PODIUM_ORDER.map((r) => byRank.get(r)).filter((e): e is SchoolEntry => Boolean(e));
  if (!shown.length) return null;

  return (
    <div className="podium">
      {shown.map((e) => (
        <div key={e.rank} className={`pod pod-${e.rank}`}>
          <div className="pod-medal" aria-hidden="true">{e.rank === 1 ? '🥇' : e.rank === 2 ? '🥈' : '🥉'}</div>
          <div className="pod-name">{e.schoolName}</div>
          {/* §5.2 — 평균 기준임을 숨기지 않는다. 인원수도 함께(§3.4). */}
          <div className="pod-time num">평균 {fmtHM(e.avgSeconds)}</div>
          <div className="pod-sub num">{e.memberCount}명</div>
          <div className="pod-bar"><span className="num">{e.rank}</span></div>
        </div>
      ))}
    </div>
  );
}

function SchoolBoard({ data }: { data: SchoolRanking }) {
  if (!data.podium.length && !data.list.length) {
    return <p className="chart-empty">이 기간에 조건을 채운 학교가 아직 없어요.</p>;
  }
  return (
    <>
      <SchoolPodium entries={data.podium} />
      {data.list.length > 0 && (
        <ul className="rank-list">
          {data.list.map((e) => (
            <li key={e.rank} className="rank-row">
              <span className="rank-n num">{e.rank}</span>
              <span className="rank-name">
                {e.schoolName}
                <span className="rank-sub num">{e.memberCount}명</span>
              </span>
              <span className="rank-time num">평균 {fmtHM(e.avgSeconds)}</span>
            </li>
          ))}
        </ul>
      )}
      {/* §3.4 — 최소 인원 미달 학교가 왜 안 보이는지 알려준다. */}
      <p className="chart-sub board-note">활동 인원 5명 이상인 학교만 순위에 올라요.</p>
    </>
  );
}

export default function RankingPage() {
  const [tab, setTab] = useState<Tab>('individual');
  const [period, setPeriod] = useState<RankingPeriod>('this_week');
  const [individual, setIndividual] = useState<IndividualRanking | null>(null);
  const [school, setSchool] = useState<SchoolRanking | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const seq = useRef(0);

  const load = useCallback(async (t: Tab, p: RankingPeriod) => {
    const mine = seq.current + 1;
    seq.current = mine;
    setLoading(true);
    setFailed(false);
    try {
      if (t === 'individual') {
        const data = await rankingApi.individual(p);
        if (mine !== seq.current) return; // 탭·기간을 연달아 바꾸면 늦게 온 응답이 최신 것을 덮는다
        setIndividual(data);
      } else {
        const data = await rankingApi.school(p);
        if (mine !== seq.current) return;
        setSchool(data);
      }
    } catch {
      if (mine === seq.current) setFailed(true);
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(tab, period);
  }, [load, tab, period]);

  // 지금 보고 있는 탭·기간의 응답인지. 아니면 이전 결과를 새 조건의 결과처럼 보여주게 된다.
  const shown = tab === 'individual' ? individual : school;
  const fresh = shown?.period === period;

  return (
    <>
      <header className="topbar">
        <div>
          <div className="hi">누가 제일 많이 공부했을까</div>
          <h1>랭킹</h1>
        </div>
      </header>

      <div className="app-body">
        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'individual'}
            className={`tab${tab === 'individual' ? ' on' : ''}`}
            onClick={() => setTab('individual')}
          >
            개인별
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'school'}
            className={`tab${tab === 'school' ? ' on' : ''}`}
            onClick={() => setTab('school')}
          >
            학교별
          </button>
        </div>

        <PeriodFilter value={period} onChange={setPeriod} />

        <section className="card">
          {failed ? (
            <div className="stack">
              <div className="state-line">랭킹을 불러오지 못했어요.</div>
              <button className="btn" onClick={() => void load(tab, period)}>다시 시도</button>
            </div>
          ) : loading || !fresh ? (
            <div className="center-msg">불러오는 중…</div>
          ) : tab === 'individual' ? (
            individual && <RankingBoard data={individual} />
          ) : (
            school && <SchoolBoard data={school} />
          )}
        </section>
      </div>
    </>
  );
}
