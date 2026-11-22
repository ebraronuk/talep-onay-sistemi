import { describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TalepListesiSayfasi } from './TalepListesiSayfasi';
import { AMIR, ekranaBas, fetchSahtele, oturumAc, PERSONEL, YONETICI } from '../test/yardimcilar';
import type { SayfaYaniti, TalepOzeti } from '../api/tipler';

function talep(id: number, baslik: string, ek: Partial<TalepOzeti> = {}): TalepOzeti {
  return {
    id,
    baslik,
    tur: 'DONANIM',
    durum: 'BEKLEMEDE',
    tutar: 1500,
    talepEdenAdSoyad: 'Ayse Yilmaz',
    birimKodu: 'BTGM',
    olusturmaTarihi: '2026-03-01T10:00:00Z',
    ...ek,
  };
}

function sayfa(icerik: TalepOzeti[], ek: Partial<SayfaYaniti<TalepOzeti>> = {}): SayfaYaniti<TalepOzeti> {
  return {
    icerik,
    sayfaNo: 0,
    sayfaBoyutu: 10,
    toplamKayit: icerik.length,
    toplamSayfa: 1,
    sonSayfaMi: true,
    ...ek,
  };
}

function parametreler(yol: string) {
  return new URLSearchParams(yol.split('?')[1]);
}

describe('Talep listesi', () => {
  it.each([
    [PERSONEL, 'Taleplerim'],
    [AMIR, 'BTGM birimi talepleri'],
    [YONETICI, 'Tüm talepler'],
  ])('%s rolune uygun baslik gosterir', async (kullanici, beklenen) => {
    oturumAc(kullanici);
    fetchSahtele({ govde: sayfa([]) });

    ekranaBas(<TalepListesiSayfasi />);

    expect(await screen.findByRole('heading', { name: beklenen })).toBeInTheDocument();
  });

  it('talepleri detay baglantisiyla listeler, tutarsiz talepte tire gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({
      govde: sayfa([talep(7, 'Ergonomik sandalye'), talep(8, 'Yillik izin', { tur: 'IZIN', tutar: null })]),
    });

    ekranaBas(<TalepListesiSayfasi />);

    const baglanti = await screen.findByRole('link', { name: 'Ergonomik sandalye' });
    expect(baglanti).toHaveAttribute('href', '/talepler/7');

    const izinSatiri = screen.getByRole('link', { name: 'Yillik izin' }).closest('tr') as HTMLElement;
    expect(within(izinSatiri).getByText('-')).toBeInTheDocument();
  });

  it('sonuc yoksa bos durum mesajini gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: sayfa([]) });

    ekranaBas(<TalepListesiSayfasi />);

    expect(await screen.findByText('Bu filtreye uyan talep yok.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('istek basarisiz olursa uyari gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ durum: 500, govde: { kod: 'SUNUCU_HATASI', mesaj: 'Beklenmeyen bir hata olustu' } });

    ekranaBas(<TalepListesiSayfasi />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Talepler yüklenemedi');
  });

  it('durum filtresi degisince istegi filtreyle ve ilk sayfadan yeniden atar', async () => {
    oturumAc(YONETICI);
    const sahte = fetchSahtele(
      { govde: sayfa([talep(1, 'Birinci')], { toplamSayfa: 2, toplamKayit: 11, sonSayfaMi: false }) },
      { govde: sayfa([talep(2, 'Ikinci')], { sayfaNo: 1, toplamSayfa: 2, toplamKayit: 11, sonSayfaMi: true }) },
      { govde: sayfa([talep(3, 'Onayli')]) },
    );

    ekranaBas(<TalepListesiSayfasi />);

    await userEvent.click(await screen.findByRole('button', { name: 'Sonraki' }));
    await screen.findByText('Ikinci');
    expect(parametreler(sahte.mock.calls[1][0]).get('page')).toBe('1');

    await userEvent.selectOptions(screen.getByLabelText('Durum'), 'ONAYLANDI');
    await screen.findByText('Onayli');

    const son = parametreler(sahte.mock.calls[2][0]);
    expect(son.get('durum')).toBe('ONAYLANDI');
    expect(son.get('page')).toBe('0');
  });

  it('filtre hizla degisirse gec gelen eski yanit yenisinin ustune yazmaz', async () => {
    oturumAc(YONETICI);
    // Yanitlari elle cozuyoruz: ilk istek en son donuyor.
    const cozucular: ((govde: unknown) => void)[] = [];
    const sahte = vi.fn(
      () =>
        new Promise((coz) => {
          cozucular.push((govde) => coz({ ok: true, status: 200, text: async () => JSON.stringify(govde) }));
        }),
    );
    vi.stubGlobal('fetch', sahte);

    ekranaBas(<TalepListesiSayfasi />);
    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(1));

    await userEvent.selectOptions(screen.getByLabelText('Durum'), 'ONAYLANDI');
    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(2));

    await act(async () => cozucular[1](sayfa([talep(2, 'Yeni liste')])));
    expect(await screen.findByText('Yeni liste')).toBeInTheDocument();

    await act(async () => cozucular[0](sayfa([talep(1, 'Eski liste')])));
    expect(screen.queryByText('Eski liste')).not.toBeInTheDocument();
    expect(screen.getByText('Yeni liste')).toBeInTheDocument();
  });
});
