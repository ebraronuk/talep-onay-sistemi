import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Menu } from './Menu';
import { ekranaBas, fetchSahtele, oturumAc, PERSONEL, YONETICI } from '../test/yardimcilar';

describe('Menu', () => {
  it('personele yeni talep baglantisini gosterir, rapor baglantisini gostermez', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: { adet: 0 } });

    ekranaBas(<Menu />);

    expect(await screen.findByRole('link', { name: 'Yeni talep' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Rapor' })).not.toBeInTheDocument();
  });

  it('yoneticiye rapor baglantisini gosterir, yeni talep baglantisini gostermez', async () => {
    oturumAc(YONETICI);
    fetchSahtele({ govde: { adet: 0 } });

    ekranaBas(<Menu />);

    expect(await screen.findByRole('link', { name: 'Rapor' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Yeni talep' })).not.toBeInTheDocument();
  });

  it('okunmamis bildirim varsa ekran okuyucu icin sayiyi metin olarak da verir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: { adet: 3 } });

    ekranaBas(<Menu />);

    expect(await screen.findByText('3 okunmamış bildirim')).toBeInTheDocument();
  });

  it('bildirim sayisi alinamazsa menu rozetsiz calismaya devam eder', async () => {
    oturumAc(PERSONEL);
    const sahte = fetchSahtele({ durum: 500, govde: { kod: 'SUNUCU_HATASI', mesaj: 'hata' } });

    ekranaBas(<Menu />);

    await waitFor(() => expect(sahte).toHaveBeenCalled());
    expect(screen.getByRole('link', { name: 'Bildirimler' })).toBeInTheDocument();
    expect(screen.queryByText(/okunmamış bildirim/)).not.toBeInTheDocument();
  });

  it('cikis oturumu temizler ve giris ekranina yonlendirir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: { adet: 0 } });

    ekranaBas(<Menu />);

    await userEvent.click(await screen.findByRole('button', { name: 'Çıkış' }));

    expect(sessionStorage.getItem('talep-onay-token')).toBeNull();
    expect(await screen.findByTestId('baska-sayfa')).toBeInTheDocument();
  });
});
