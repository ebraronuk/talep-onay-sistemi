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
    coverage: {
      provider: 'v8',
      // Yalnizca testlerin yukledigi dosyalar degil, src altindaki her sey sayilsin;
      // yoksa hic test edilmeyen sayfalar rakami sisirir.
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.*', 'src/test/**', 'src/main.tsx', 'src/**/*.d.ts'],
      // Esikler olculen degerin hemen altinda (satir %84, dal %76, fonksiyon %77).
      // Hedef degil, gerilemeye karsi taban.
      thresholds: { lines: 80, statements: 78, branches: 73, functions: 73 },
    },
  },
});
