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
        theme_color: '#1e293b',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        // 아이콘(192/512)은 이후 실제 이미지를 public/ 에 넣고 여기 등록 (PWA 설치 조건)
        icons: [],
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
