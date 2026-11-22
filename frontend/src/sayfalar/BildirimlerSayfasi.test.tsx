import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BildirimlerSayfasi } from './BildirimlerSayfasi';
import { ekranaBas, fetchSahtele, oturumAc, PERSONEL } from '../test/yardimcilar';
import type { Bildirim, SayfaYaniti } from '../api/tipler';

function sayfa(icerik: Bildirim[]): SayfaYaniti<Bildirim> {
  return { icerik, sayfaNo: 0, sayfaBoyutu: 20, toplamKayit: icerik.length, toplamSayfa: 1, sonSayfaMi: true };
}

const OKUNMAMIS: Bildirim = {
  id: 5,
  mesaj: 'Talebiniz onaylandi',
  okundu: false,
  talepId: 7,
  olusturmaTarihi: '2026-03-01T10:00:00Z',
};

describe('Bildirimler', () => {
  it('okundu isaretleyince istegi atar ve listeyi yeniden ceker', async () => {
    oturumAc(PERSONEL);
    const sahte = fetchSahtele(
      { govde: sayfa([OKUNMAMIS]) },
      { durum: 204 },
      { govde: sayfa([{ ...OKUNMAMIS, okundu: true }]) },
    );

    ekranaBas(<BildirimlerSayfasi />);

    expect(await screen.findByRole('link', { name: 'Talebi aç' })).toHaveAttribute('href', '/talepler/7');
    await userEvent.click(screen.getByRole('button', { name: 'Okundu' }));

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Okundu' })).not.toBeInTheDocument());
    expect(sahte.mock.calls[1][0]).toBe('/api/v1/bildirimler/5/okundu');
    expect(sahte.mock.calls[1][1].method).toBe('POST');
    expect(sahte).toHaveBeenCalledTimes(3);
  });

  it('bildirim yoksa bos durum mesajini gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: sayfa([]) });

    ekranaBas(<BildirimlerSayfasi />);

    expect(await screen.findByText('Bildiriminiz yok.')).toBeInTheDocument();
  });

  it('istek basarisiz olursa uyari gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ durum: 500, govde: { kod: 'SUNUCU_HATASI', mesaj: 'hata' } });

    ekranaBas(<BildirimlerSayfasi />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Bildirimler yüklenemedi');
  });
});
