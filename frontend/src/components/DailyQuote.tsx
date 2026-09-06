import { useMemo } from 'react';
import { todaysQuote } from '../lib/quotes';

/** 홈 하단 "오늘의 명언" 카드. 매일 하나씩 고정 노출(백엔드 없음). */
export default function DailyQuote() {
  const quote = useMemo(() => todaysQuote(), []);
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
