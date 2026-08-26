import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Gelistirmede /api istekleri arka uca yonlendiriliyor. Boylece tarayici
    // acisindan her sey ayni kaynaktan geliyor ve CORS devreye girmiyor.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/kurulum.ts',
    css: false,
  },
});
