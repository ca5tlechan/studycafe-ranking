import type { IndividualRanking, RankEntry } from '../lib/api';
import { fmtHM } from '../lib/format';

/** 포디움 표시 순서: 2·1·3 (가운데가 1등). 참여자가 3명 미만이면 있는 만큼만. */
const PODIUM_ORDER = [2, 1, 3];

function Podium({ entries }: { entries: RankEntry[] }) {
  const byRank = new Map(entries.map((e) => [e.rank, e]));
  const shown = PODIUM_ORDER.map((r) => byRank.get(r)).filter((e): e is RankEntry => Boolean(e));
  if (!shown.length) return null;

  return (
    <div className="podium">
      {shown.map((e) => (
        <div key={e.rank} className={`pod pod-${e.rank}${e.isMe ? ' me' : ''}`}>
          <div className="pod-medal" aria-hidden="true">{e.rank === 1 ? '🥇' : e.rank === 2 ? '🥈' : '🥉'}</div>
          <div className="pod-name">{e.displayName}</div>
          <div className="pod-time num">{fmtHM(e.seconds)}</div>
          <div className="pod-bar"><span className="num">{e.rank}</span></div>
        </div>
      ))}
    </div>
  );
}

function RankRow({ entry }: { entry: RankEntry }) {
  return (
    <li className={`rank-row${entry.isMe ? ' me' : ''}`}>
      <span className="rank-n num">{entry.rank}</span>
      <span className="rank-name">{entry.displayName}</span>
      <span className="rank-time num">{fmtHM(entry.seconds)}</span>
    </li>
  );
}

/**
 * 개인 랭킹 화면(§5.2). 랭킹 탭과 우리 학교 페이지가 같은 구성이라 공유한다.
 * §5.3 은 대상만 내 학교로 좁힌 같은 화면이다.
 */
export default function RankingBoard({ data }: { data: IndividualRanking }) {
  const { podium, list, myRank } = data;
  const inTopTen = myRank !== null && [...podium, ...list].some((e) => e.isMe);

  if (!podium.length && !list.length) {
    return (
      <>
        <p className="chart-empty">이 기간에는 아직 기록이 없어요.</p>
        <MyRank myRank={myRank} inTopTen={false} />
      </>
    );
  }

  return (
    <>
      <Podium entries={podium} />
      {list.length > 0 && (
        <ul className="rank-list">
          {list.map((e) => <RankRow key={e.rank} entry={e} />)}
        </ul>
      )}
      <MyRank myRank={myRank} inTopTen={inTopTen} />
    </>
  );
}

/** §5.2 — 10위 밖이어도 내 순위는 항상 보여준다. 기록이 없으면 0등이 아니라 '순위 없음'. */
function MyRank({ myRank, inTopTen }: { myRank: RankEntry | null; inTopTen: boolean }) {
  return (
    <div className="my-rank">
      {myRank ? (
        <>
          <span className="my-rank-lbl">내 순위</span>
          <span className="my-rank-v">
            <b className="num">{myRank.rank}등</b>
            {inTopTen && <span className="my-rank-hint">위에 표시돼 있어요</span>}
          </span>
          <span className="rank-time num">{fmtHM(myRank.seconds)}</span>
        </>
      ) : (
        <>
          <span className="my-rank-lbl">내 순위</span>
          <span className="my-rank-v"><b>순위 없음</b></span>
          <span className="rank-time">기록 없음</span>
        </>
      )}
    </div>
  );
}
