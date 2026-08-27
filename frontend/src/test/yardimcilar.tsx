import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactElement } from 'react';
import { OturumSaglayici } from '../kimlik/OturumBaglami';
import type { KullaniciOzeti } from '../api/tipler';
import { vi } from 'vitest';

export const PERSONEL: KullaniciOzeti = {
  id: 1,
  kullaniciAdi: 'ayse.yilmaz',
  adSoyad: 'Ayse Yilmaz',
  rol: 'PERSONEL',
  birimKodu: 'BTGM',
};

export const AMIR: KullaniciOzeti = {
  id: 2,
  kullaniciAdi: 'ali.vural',
  adSoyad: 'Ali Vural',
  rol: 'AMIR',
  birimKodu: 'BTGM',
};

export const YONETICI: KullaniciOzeti = {
  id: 3,
  kullaniciAdi: 'hakan.ozturk',
  adSoyad: 'Hakan Ozturk',
  rol: 'YONETICI',
  birimKodu: 'BTGM',
};

/** Oturumu acikmis gibi kurar. Bileseni gercek giris akisindan gecirmeye gerek kalmiyor. */
export function oturumAc(kullanici: KullaniciOzeti) {
  sessionStorage.setItem('talep-onay-token', 'test-token');
  sessionStorage.setItem('talep-onay-kullanici', JSON.stringify(kullanici));
}

interface Secenekler {
  yol?: string;
  desen?: string;
}

export function ekranaBas(bilesen: ReactElement, { yol = '/', desen = '/' }: Secenekler = {}) {
  return render(
    <OturumSaglayici>
      <MemoryRouter initialEntries={[yol]}>
        <Routes>
          <Route path={desen} element={bilesen} />
          <Route path="*" element={<div data-testid="baska-sayfa" />} />
        </Routes>
      </MemoryRouter>
    </OturumSaglayici>,
  );
}

/**
 * fetch'i sahteleyip sirayla verilen yanitlari dondurur.
 * Gercek bir HTTP istemcisi kurmak yerine bunu tercih ettim: testin ilgilendigi sey
 * bilesenin ne gonderdigi ve gelen yanitla ne yaptigi.
 */
export function fetchSahtele(...yanitlar: { durum?: number; govde?: unknown }[]) {
  const sahte = vi.fn();
  yanitlar.forEach(({ durum = 200, govde = null }) => {
    sahte.mockResolvedValueOnce({
      ok: durum >= 200 && durum < 300,
      status: durum,
      text: async () => (govde === null ? '' : JSON.stringify(govde)),
    });
  });
  vi.stubGlobal('fetch', sahte);
  return sahte;
}
