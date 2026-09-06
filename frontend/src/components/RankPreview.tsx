import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { rankingApi, type RankEntry, type SchoolEntry } from '../lib/api';
import { fmtHM } from '../lib/format';

type Loadable<T> = T | null | undefined; // undefined=로딩, null=기록/순위 없음

/**
 * 홈 "이번 주 랭킹" 미리보기 — 내 순위(+ 소속이 있으면 우리 학교 순위)를 한 카드에 두 줄로.
 * 각 줄을 탭하면 해당 랭킹으로 이동. 개인 순위조차 못 받으면 홈을 막지 않도록 카드를 숨긴다.
 */
export default function RankPreview() {
  const { user } = useAuth();
  const hasSchool = !!user?.schoolName;
  const [mine, setMine] = useState<Loadable<RankEntry>>(undefined);
  const [school, setSchool] = useState<Loadable<SchoolEntry>>(undefined);
  const [failed, setFailed] = useState(false);
  const [schoolFailed, setSchoolFailed] = useState(false); // 학교 로드 실패 — '순위 없음'과 구분

  useEffect(() => {
    let alive = true;
    rankingApi
      .individual('this_week')
      .then((d) => alive && setMine(d.myRank))
      .catch(() => alive && setFailed(true));
    if (hasSchool) {
      rankingApi
        .school('this_week')
        .then((d) => {
          if (!alive) return;
          // 학교별 보드(상위권)에서 우리 학교를 찾는다. 없으면(순위권 밖·최소인원 미달) null.
          setSchool([...d.podium, ...d.list].find((e) => e.schoolName === user!.schoolName) ?? null);
        })
        .catch(() => alive && setSchoolFailed(true)); // 로드 실패는 '순위 없음'과 구분해 그 줄을 숨긴다
    }
    return () => {
      alive = false;
    };
  }, [hasSchool, user?.schoolName]);

  if (failed) return null;

  return (
    <div className="card rank-card">
      <div className="rank-card-head">이번 주 랭킹</div>
      <Link to="/ranking" className="rank-row2">
        <span className="rank-row2-lbl">내 순위</span>
        <span className="rank-row2-v">
          {mine === undefined ? (
            <span className="rank-preview-dim">불러오는 중…</span>
          ) : mine === null ? (
            <span className="rank-preview-dim">아직 기록이 없어요</span>
          ) : (
            <>
              <b className="num">{mine.rank}등</b> · <span className="num">{fmtHM(mine.seconds)}</span>
            </>
          )}
          <span className="rank-preview-go" aria-hidden="true">›</span>
        </span>
      </Link>
      {hasSchool && !schoolFailed && (
        <Link to="/ranking?tab=school" className="rank-row2">
          <span className="rank-row2-lbl">우리 학교</span>
          <span className="rank-row2-v">
            {school === undefined ? (
              <span className="rank-preview-dim">불러오는 중…</span>
            ) : school === null ? (
              <span className="rank-preview-dim">아직 순위에 없어요</span>
            ) : (
              <>
                <b className="num">{school.rank}등</b> · 평균 <span className="num">{fmtHM(school.avgSeconds)}</span>
              </>
            )}
            <span className="rank-preview-go" aria-hidden="true">›</span>
          </span>
        </Link>
      )}
    </div>
  );
}
