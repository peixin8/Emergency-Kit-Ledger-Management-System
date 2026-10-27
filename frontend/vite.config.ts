import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { host: '0.0.0.0', proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } } },
  test: { environment: 'jsdom', globals: true, server: { deps: { inline: ['element-plus', 'async-validator'] } } }
})
