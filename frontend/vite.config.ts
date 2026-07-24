import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // Workbox 가 생성하는 서비스워커 최상단에 push 핸들러를 주입한다(§3.6b Web Push).
      // public/push-sw.js 는 그대로 정적 배포되고, 생성 SW 가 importScripts 로 불러온다.
      workbox: {
        importScripts: ['push-sw.js'],
      },
      manifest: {
        name: '스터디카페 랭킹',
        short_name: '스카랭킹',
        description: '스터디카페 공부 시간 기록 & 랭킹',
        theme_color: '#1c4fa2',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        // 시상대+별 앱 아이콘. any(정사각) + maskable(안드로이드 원형 마스크용 여백 포함).
        icons: [
          { src: '/pwa-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    // 개발 중 프론트(:5173)에서 /api 호출을 백엔드(:8080)로 프록시
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
