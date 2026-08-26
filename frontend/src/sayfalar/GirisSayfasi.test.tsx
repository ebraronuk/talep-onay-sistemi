import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GirisSayfasi } from './GirisSayfasi';
import { ekranaBas, fetchSahtele } from '../test/yardimcilar';

describe('Giris akisi', () => {
  it('dogru bilgilerle giris yapinca token saklanir', async () => {
    const sahte = fetchSahtele({
      govde: {
        token: 'jwt-token',
        tip: 'Bearer',
        gecerlilikSaniye: 3600,
        kullanici: {
          id: 1,
          kullaniciAdi: 'ayse.yilmaz',
          adSoyad: 'Ayse Yilmaz',
          rol: 'PERSONEL',
          birimKodu: 'BTGM',
        },
      },
    });

    ekranaBas(<GirisSayfasi />);

    await userEvent.type(screen.getByLabelText('Kullanıcı adı'), 'ayse.yilmaz');
    await userEvent.type(screen.getByLabelText('Şifre'), 'Parola123!');
    await userEvent.click(screen.getByRole('button', { name: 'Giriş yap' }));

    await waitFor(() => expect(sessionStorage.getItem('talep-onay-token')).toBe('jwt-token'));

    const [yol, ayarlar] = sahte.mock.calls[0];
    expect(yol).toBe('/api/kimlik/giris');
    expect(JSON.parse(ayarlar.body)).toEqual({ kullaniciAdi: 'ayse.yilmaz', sifre: 'Parola123!' });
  });

  it('yanlis sifrede hata mesaji gosterir ve token saklamaz', async () => {
    fetchSahtele({
      durum: 401,
      govde: { kod: 'KIMLIK_DOGRULANAMADI', mesaj: 'Kullanici adi veya sifre hatali', detaylar: [] },
    });

    ekranaBas(<GirisSayfasi />);

    await userEvent.type(screen.getByLabelText('Kullanıcı adı'), 'ayse.yilmaz');
    await userEvent.type(screen.getByLabelText('Şifre'), 'yanlis');
    await userEvent.click(screen.getByRole('button', { name: 'Giriş yap' }));

    // Giris ucundeki 401 "oturum dustu" degil "sifre yanlis" demek; kullaniciya
    // arka ucun mesaji aynen gosteriliyor.
    expect(await screen.findByRole('alert')).toHaveTextContent('Kullanici adi veya sifre hatali');
    expect(sessionStorage.getItem('talep-onay-token')).toBeNull();
  });
});
