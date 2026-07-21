import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 백엔드에 CORS 설정이 없어 dev 프록시로 우회한다 (api.js는 /api 상대경로 호출)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
