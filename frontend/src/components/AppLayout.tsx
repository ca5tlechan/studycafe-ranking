import { NavLink, Outlet } from 'react-router-dom';
import type { ReactNode } from 'react';

const HomeIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20h14V9.5" /></svg>
);
const MyIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="17" rx="2.5" /><path d="M3 9h18M8 2v4M16 2v4" /></svg>
);
const RankIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 20V10M12 20V4M20 20v-7" /></svg>
);
const SchoolIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 10l9-6 9 6-9 5-9-5Z" /><path d="M7 12.5V17c0 1 2.2 2.4 5 2.4s5-1.4 5-2.4v-4.5" /></svg>
);

const tabs: { to: string; label: string; icon: ReactNode; end?: boolean }[] = [
  { to: '/', label: '홈', icon: HomeIcon, end: true },
  { to: '/my', label: '마이', icon: MyIcon },
  { to: '/ranking', label: '랭킹', icon: RankIcon },
  { to: '/school', label: '우리학교', icon: SchoolIcon },
];

export default function AppLayout() {
  return (
    <div className="shell">
      <div className="page">
        <Outlet />
      </div>
      <nav className="bottom-nav">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end}>
            {t.icon}
            {t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
