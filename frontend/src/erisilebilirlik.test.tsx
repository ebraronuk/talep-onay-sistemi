import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import axe from 'axe-core';
import type { ReactElement } from 'react';
import { GirisSayfasi } from './sayfalar/GirisSayfasi';
import { YeniTalepSayfasi } from './sayfalar/YeniTalepSayfasi';
import { TalepListesiSayfasi } from './sayfalar/TalepListesiSayfasi';
import { BildirimlerSayfasi } from './sayfalar/BildirimlerSayfasi';
import { RaporSayfasi } from './sayfalar/RaporSayfasi';
import { ekranaBas, fetchSahtele, oturumAc, PERSONEL, YONETICI } from './test/yardimcilar';

// jsdom yerlesim hesaplamadigi icin renk kontrasti burada olculemez; o kontrol
// tarayicida yapilmali. Geri kalan kurallar (etiket, rol, baslik sirasi, ad) gecerli.
async function ihlaller(container: HTMLElement) {
  const sonuc = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });
  return sonuc.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
}

const BOS_SAYFA = { icerik: [], sayfaNo: 0, sayfaBoyutu: 20, toplamKayit: 0, toplamSayfa: 1, sonSayfaMi: true };

const LISTE = {
  ...BOS_SAYFA,
  icerik: [
    {
      id: 1,
      baslik: 'Ergonomik sandalye',
      tur: 'DONANIM',
      durum: 'BEKLEMEDE',
      tutar: 1500,
      talepEdenAdSoyad: 'Ayse Yilmaz',
      birimKodu: 'BTGM',
      olusturmaTarihi: '2026-03-01T10:00:00Z',
    },
  ],
  toplamKayit: 1,
};

const RAPOR = {
  birimId: null,
  toplamTalep: 3,
  bekleyenTalep: 1,
  durumDagilimi: [{ durum: 'BEKLEMEDE', adet: 1 }],
};

describe('Erisilebilirlik (axe)', () => {
  // Asagidaki "ihlal yok" testleri axe hic calismasa da gecerdi; bu test kontrolun bos olmadigini gosteriyor.
  it('etiketsiz alan ve adsiz dugmeyi yakalar', async () => {
    const { container } = ekranaBas(
      <form>
        <input type="text" />
        <button type="submit" />
      </form>,
    );

    const bulunan = (await ihlaller(container)).map((i) => i.split(':')[0]);
    expect(bulunan).toEqual(expect.arrayContaining(['label', 'button-name']));
  });

  it.each<[string, () => ReactElement, unknown, string]>([
    ['giris', () => <GirisSayfasi />, null, 'Giriş yap'],
    ['yeni talep', () => <YeniTalepSayfasi />, null, 'Taslak olarak kaydet'],
  ])('%s formunda ihlal yok', async (_ad, bilesen, _govde, dugme) => {
    oturumAc(PERSONEL);

    const { container } = ekranaBas(bilesen());
    await screen.findByRole('button', { name: dugme });

    expect(await ihlaller(container)).toEqual([]);
  });

  it.each<[string, () => ReactElement, unknown, string]>([
    ['talep listesi', () => <TalepListesiSayfasi />, LISTE, 'Ergonomik sandalye'],
    ['bildirimler', () => <BildirimlerSayfasi />, BOS_SAYFA, 'Bildiriminiz yok.'],
    ['rapor', () => <RaporSayfasi />, RAPOR, 'toplam talep'],
  ])('%s sayfasinda ihlal yok', async (_ad, bilesen, govde, beklenenMetin) => {
    oturumAc(YONETICI);
    fetchSahtele({ govde });

    const { container } = ekranaBas(bilesen());
    await screen.findByText(beklenenMetin);

    expect(await ihlaller(container)).toEqual([]);
  });
});
