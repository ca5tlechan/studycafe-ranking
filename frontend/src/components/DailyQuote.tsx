import { useEffect, useState } from 'react';
import { todaysQuote } from '../lib/quotes';

/** 홈 하단 "오늘의 명언" 카드. 매일 하나씩 고정 노출(백엔드 없음). */
export default function DailyQuote() {
  const [quote, setQuote] = useState(() => todaysQuote());

  // 앱을 다시 포그라운드로 가져오면(자정을 넘겨 재실행·전환) 그날 문구로 갱신한다.
  // 같은 날이면 todaysQuote()가 같은 객체를 돌려줘 리렌더가 일어나지 않는다.
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') setQuote(todaysQuote());
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);
  return (
    <div className="card quote-card">
      <div className="quote-head">오늘의 한마디</div>
      <p className="quote-text">
        <span className="quote-q" aria-hidden="true">&ldquo;</span>{quote.text}<span className="quote-q" aria-hidden="true">&rdquo;</span>
      </p>
      {quote.author && <p className="quote-author">— {quote.author}</p>}
    </div>
  );
}
