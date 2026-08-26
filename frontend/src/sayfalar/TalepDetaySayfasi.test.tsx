import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TalepDetaySayfasi } from './TalepDetaySayfasi';
import { AMIR, ekranaBas, fetchSahtele, oturumAc, PERSONEL } from '../test/yardimcilar';
import type { TalepDetayi } from '../api/tipler';

const BEKLEYEN_TALEP: TalepDetayi = {
  id: 5,
  baslik: 'Yillik izin talebi',
  aciklama: '15-22 Eylul arasi izin',
  tur: 'IZIN',
  durum: 'BEKLEMEDE',
  talepEden: PERSONEL,
  birimKodu: 'BTGM',
  birimAdi: 'Bilgi Teknolojileri',
  olusturmaTarihi: '2026-08-20T10:00:00Z',
  guncellemeTarihi: '2026-08-20T10:05:00Z',
  gecmis: [
    {
      id: 1,
      oncekiDurum: null,
      yeniDurum: 'TASLAK',
      islemYapanAdSoyad: 'Ayse Yilmaz',
      aciklama: 'Talep olusturuldu',
      islemTarihi: '2026-08-20T10:00:00Z',
    },
    {
      id: 2,
      oncekiDurum: 'TASLAK',
      yeniDurum: 'BEKLEMEDE',
      islemYapanAdSoyad: 'Ayse Yilmaz',
      aciklama: 'Onaya gonderildi',
      islemTarihi: '2026-08-20T10:05:00Z',
    },
  ],
};

function detayEkraniniAc() {
  return ekranaBas(<TalepDetaySayfasi />, { yol: '/talepler/5', desen: '/talepler/:id' });
}

describe('Talep detayi ve onay akisi', () => {
  it('amir bekleyen talebi onaylayabilir', async () => {
    oturumAc(AMIR);
    const sahte = fetchSahtele(
      { govde: BEKLEYEN_TALEP },
      { govde: { ...BEKLEYEN_TALEP, durum: 'ONAYLANDI' } },
      { govde: { ...BEKLEYEN_TALEP, durum: 'ONAYLANDI' } },
    );

    detayEkraniniAc();

    await userEvent.type(await screen.findByLabelText(/Gerekçe/), 'Uygundur');
    await userEvent.click(screen.getByRole('button', { name: 'Onayla' }));

    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(3));

    const [yol, ayarlar] = sahte.mock.calls[1];
    expect(yol).toBe('/api/talepler/5/karar');
    expect(JSON.parse(ayarlar.body)).toEqual({ karar: 'ONAYLA', aciklama: 'Uygundur' });

    expect(await screen.findByText('Onaylandı')).toBeInTheDocument();
  });

  it('amir reddedince gerekce govdeye konur', async () => {
    oturumAc(AMIR);
    const sahte = fetchSahtele(
      { govde: BEKLEYEN_TALEP },
      { govde: { ...BEKLEYEN_TALEP, durum: 'REDDEDILDI' } },
      { govde: { ...BEKLEYEN_TALEP, durum: 'REDDEDILDI' } },
    );

    detayEkraniniAc();

    await userEvent.type(await screen.findByLabelText(/Gerekçe/), 'Butce yok');
    await userEvent.click(screen.getByRole('button', { name: 'Reddet' }));

    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(3));
    expect(JSON.parse(sahte.mock.calls[1][1].body)).toEqual({
      karar: 'REDDET',
      aciklama: 'Butce yok',
    });
  });

  it('personel karar dugmelerini gormez', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: BEKLEYEN_TALEP });

    detayEkraniniAc();

    expect(await screen.findByText('Yillik izin talebi')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Onayla' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reddet' })).not.toBeInTheDocument();
  });

  it('taslak talebin sahibi onaya gonder dugmesini gorur', async () => {
    oturumAc(PERSONEL);
    const taslak: TalepDetayi = { ...BEKLEYEN_TALEP, durum: 'TASLAK' };
    const sahte = fetchSahtele(
      { govde: taslak },
      { govde: { ...taslak, durum: 'BEKLEMEDE' } },
      { govde: { ...taslak, durum: 'BEKLEMEDE' } },
    );

    detayEkraniniAc();

    await userEvent.click(await screen.findByRole('button', { name: 'Onaya gönder' }));

    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(3));
    expect(sahte.mock.calls[1][0]).toBe('/api/talepler/5/onaya-gonder');
  });

  it('onay gecmisi kronolojik listeleniyor', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: BEKLEYEN_TALEP });

    detayEkraniniAc();

    expect(await screen.findByText('Talep olusturuldu')).toBeInTheDocument();
    expect(screen.getByText('Onaya gonderildi')).toBeInTheDocument();
  });

  it('yetkisiz erisimde hata mesaji gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({
      durum: 403,
      govde: { kod: 'YETKISIZ_ISLEM', mesaj: 'Bu talebi goruntuleme yetkiniz yok', detaylar: [] },
    });

    detayEkraniniAc();

    expect(await screen.findByRole('alert')).toHaveTextContent('Bu talebi goruntuleme yetkiniz yok');
  });
});
