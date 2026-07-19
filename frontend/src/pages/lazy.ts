import { lazy } from 'react';

// 무거운 라이브러리를 물고 있는 화면은 그 화면에 들어갈 때 받는다.
// (html5-qrcode·recharts 를 첫 로딩에 포함하면 번들이 3배가 된다 — 폰에서 쓰는 PWA다.)
export const CheckInPage = lazy(() => import('./CheckInPage'));
export const MyPage = lazy(() => import('./MyPage'));
// 관리자 화면은 운영자만 쓰므로 첫 로딩에 넣지 않는다.
export const AdminPage = lazy(() => import('./AdminPage'));
