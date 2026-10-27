import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// 编译目标仅微信小程序（见 前端-00-基础设施.md F1 / §12）。
// 如需本地 H5 预览可 `npm run dev:h5`，但生产只打 mp-weixin。
export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true,
  },
})
