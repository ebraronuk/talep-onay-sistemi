import { defineConfig, devices } from '@playwright/test';

// Uctan uca testler gercek yigina karsi kosar: once `docker compose up --build`.
// Testler demo verisindeki kullanicilarla giris yapar ve her calismada yeni talep
// acar; ayni veritabaninda tekrar tekrar kosturulabilir.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // Ayni demo kullanicilariyla calisiyorlar; paralel kosunca bildirim sayilari
  // gibi paylasilan durumlar birbirini etkiler.
  workers: 1,
  // Kirmizi test yeniden denenerek yesile cevrilmez; dalgalanan test duzeltilir.
  retries: 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_ADRES ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
