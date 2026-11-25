// README'deki ekran goruntulerini uretir. Elle kirpilmis gorsel tutmak yerine
// script tutuluyor: arayuz degistiginde goruntuler tek komutla yenilenir ve
// hangi veriyle alindiklari bellidir (demo profili tohum verisi).
//
// Kullanim: depo kokunde `docker compose up -d --wait`, sonra
//   cd frontend && npm run ekran
import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

const ADRES = process.env.EKRAN_ADRES ?? 'http://localhost:3000';
const SIFRE = 'Parola123!';
const HEDEF = new URL('../../docs/ekranlar/', import.meta.url).pathname;

async function girisYap(sayfa, kullaniciAdi) {
  await sayfa.goto(`${ADRES}/giris`);
  await sayfa.getByLabel('Kullanıcı adı').fill(kullaniciAdi);
  await sayfa.getByLabel('Şifre').fill(SIFRE);
  await sayfa.getByRole('button', { name: 'Giriş yap' }).click();
  await sayfa.getByRole('button', { name: 'Çıkış' }).waitFor();
}

/**
 * `baslik`, hedef sayfanin kendi basligi. Beklemeden olcmek yaniltiyor: tek
 * sayfa uygulamasinda adres aninda degisiyor ama React henuz yeniden cizmemis
 * oluyor, dolayisiyla olcu bir onceki sayfanin yuksekligini veriyor.
 */
async function cek(sayfa, ad, baslik) {
  await sayfa.getByRole('heading', { name: baslik }).waitFor();
  await sayfa.waitForLoadState('networkidle');

  // Sabit yukseklik yerine icerigin bittigi yeri olcuyoruz; boylece her ekran
  // kendi boyunda cikiyor ve arayuz degisince goruntuyu elle kirpmak gerekmiyor.
  // Olcu main'in son cocugunun alti: .icerik esnek degil, icerik kadar buyuyor.
  const alt = await sayfa.evaluate(() => {
    const son = document.querySelector('main')?.lastElementChild;
    return son ? Math.ceil(son.getBoundingClientRect().bottom) : document.body.scrollHeight;
  });

  await sayfa.screenshot({
    path: `${HEDEF}${ad}.png`,
    fullPage: true,
    clip: { x: 0, y: 0, width: 1280, height: alt + 28 },
  });
  console.log('yazildi:', ad);
}

const tarayici = await chromium.launch();
const baglam = await tarayici.newContext({
  viewport: { width: 1280, height: 800 },
  deviceScaleFactor: 2,
  locale: 'tr-TR',
});
const sayfa = await baglam.newPage();
await mkdir(HEDEF, { recursive: true });

// Yonetici tum birimleri gorur: liste, ikinci kademe onay ekrani, ozet rapor.
await girisYap(sayfa, 'hakan.ozturk');
await cek(sayfa, 'talep-listesi', 'Tüm talepler');

await sayfa.getByRole('link', { name: /Sunucu yenileme/ }).click();
await cek(sayfa, 'onay-ekrani', /Sunucu yenileme/);

await sayfa.getByRole('link', { name: 'Rapor' }).click();
await cek(sayfa, 'yonetici-raporu', 'Özet rapor');

await tarayici.close();
