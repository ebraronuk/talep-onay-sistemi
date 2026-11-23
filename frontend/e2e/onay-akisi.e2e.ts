import { expect, test, type Page } from '@playwright/test';

const SIFRE = 'Parola123!';

async function girisYap(page: Page, kullaniciAdi: string) {
  await page.goto('/giris');
  await page.getByLabel('Kullanıcı adı').fill(kullaniciAdi);
  await page.getByLabel('Şifre').fill(SIFRE);
  await page.getByRole('button', { name: 'Giriş yap' }).click();
  await expect(page.getByRole('button', { name: 'Çıkış' })).toBeVisible();
}

async function cikisYap(page: Page) {
  await page.getByRole('button', { name: 'Çıkış' }).click();
  await expect(page.getByLabel('Kullanıcı adı')).toBeVisible();
}

/** Personel olarak talep acar, onaya gonderir ve detay adresini dondurur. */
async function talepAcVeGonder(page: Page, baslik: string, tutar: string | null) {
  await girisYap(page, 'ayse.yilmaz');
  await page.getByRole('link', { name: 'Yeni talep' }).click();
  await page.getByLabel('Başlık').fill(baslik);
  await page.getByLabel('Tür').selectOption(tutar ? 'DONANIM' : 'IZIN');
  if (tutar) await page.getByLabel(/Tutar/).fill(tutar);
  await page.getByLabel('Açıklama').fill('Uctan uca test');
  await page.getByRole('button', { name: 'Taslak olarak kaydet' }).click();

  await expect(page.getByRole('heading', { name: baslik })).toBeVisible();
  await page.getByRole('button', { name: 'Onaya gönder' }).click();
  await expect(durum(page)).toHaveText('Amir onayında');

  const adres = page.url();
  await cikisYap(page);
  return adres;
}

const durum = (page: Page) => page.locator('.sayfa-basligi .rozet');

test('limiti asan talep amir ve yonetici onayindan gecer, her adim denetim izine yazilir', async ({ page }) => {
  // Varsayilan yonetici limiti 50.000 TL.
  const adres = await talepAcVeGonder(page, `Sunucu dolabi ${Date.now()}`, '75000');

  await girisYap(page, 'ali.vural');
  await page.goto(adres);
  await page.getByRole('button', { name: 'Onayla' }).click();
  await expect(durum(page)).toHaveText('Yönetici onayında');
  // Ikinci kademe amirin degil; dugme kalkmali.
  await expect(page.getByRole('button', { name: 'Onayla' })).toHaveCount(0);
  await cikisYap(page);

  await girisYap(page, 'hakan.ozturk');
  await page.goto(adres);
  await page.getByRole('button', { name: 'Onayla' }).click();
  await expect(durum(page)).toHaveText('Onaylandı');

  const gecmis = page.locator('.gecmis li');
  await expect(gecmis).toHaveCount(4);
  await expect(gecmis.nth(2)).toContainText('Ali Vural');
  await expect(gecmis.nth(3)).toContainText('Hakan Ozturk');
});

test('amir gerekce yazmadan reddedemez, gerekceyle reddedince talep sahibi gorur', async ({ page }) => {
  const baslik = `Yillik izin ${Date.now()}`;
  const adres = await talepAcVeGonder(page, baslik, null);

  await girisYap(page, 'ali.vural');
  await page.goto(adres);
  await page.getByRole('button', { name: 'Reddet' }).click();
  await expect(page.getByRole('alert')).toContainText('gerekce');
  await expect(durum(page)).toHaveText('Amir onayında');

  await page.getByLabel(/Gerekçe/).fill('Proje teslim haftasi');
  await page.getByRole('button', { name: 'Reddet' }).click();
  await expect(durum(page)).toHaveText('Reddedildi');
  await cikisYap(page);

  await girisYap(page, 'ayse.yilmaz');
  await page.goto(adres);
  await expect(durum(page)).toHaveText('Reddedildi');
  await expect(page.locator('.gecmis')).toContainText('Proje teslim haftasi');
});
