import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TalepDetaySayfasi } from './TalepDetaySayfasi';
import { AMIR, ekranaBas, fetchSahtele, oturumAc, PERSONEL, YONETICI } from '../test/yardimcilar';
import type { TalepDetayi } from '../api/tipler';

const BEKLEYEN_TALEP: TalepDetayi = {
  id: 5,
  baslik: 'Yillik izin talebi',
  aciklama: '15-22 Eylul arasi izin',
  tur: 'IZIN',
  durum: 'BEKLEMEDE',
  tutar: null,
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

const YONETICI_ONAYINDA_TALEP: TalepDetayi = {
  ...BEKLEYEN_TALEP,
  id: 6,
  baslik: 'Sunucu yenileme',
  tur: 'SATIN_ALMA',
  durum: 'YONETICI_ONAYINDA',
  tutar: 120000,
  gecmis: [
    ...BEKLEYEN_TALEP.gecmis,
    {
      id: 3,
      oncekiDurum: 'BEKLEMEDE',
      yeniDurum: 'YONETICI_ONAYINDA',
      islemYapanAdSoyad: 'Ali Vural',
      aciklama: 'Birim ihtiyaci uygun, tutar limiti asiyor.',
      islemTarihi: '2026-08-20T11:00:00Z',
    },
  ],
};

function detayEkraniniAcId(id: number) {
  return ekranaBas(<TalepDetaySayfasi />, { yol: `/talepler/${id}`, desen: '/talepler/:id' });
}

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
    expect(yol).toBe('/api/v1/talepler/5/karar');
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
    expect(sahte.mock.calls[1][0]).toBe('/api/v1/talepler/5/onaya-gonder');
  });

  it('onay gecmisi kronolojik listeleniyor', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({ govde: BEKLEYEN_TALEP });

    detayEkraniniAc();

    expect(await screen.findByText('Talep olusturuldu')).toBeInTheDocument();
    expect(screen.getByText('Onaya gonderildi')).toBeInTheDocument();
  });

  it('tutari olan talebin tutari gorunuyor', async () => {
    oturumAc(YONETICI);
    fetchSahtele({ govde: YONETICI_ONAYINDA_TALEP });

    detayEkraniniAcId(6);

    expect(await screen.findByText('Yönetici onayında')).toBeInTheDocument();
    expect(screen.getByText('₺120.000,00')).toBeInTheDocument();
  });

  it('yonetici tutar limitini asan talebi onaylayabilir', async () => {
    oturumAc(YONETICI);
    const sahte = fetchSahtele(
      { govde: YONETICI_ONAYINDA_TALEP },
      { govde: { ...YONETICI_ONAYINDA_TALEP, durum: 'ONAYLANDI' } },
      { govde: { ...YONETICI_ONAYINDA_TALEP, durum: 'ONAYLANDI' } },
    );

    detayEkraniniAcId(6);

    await userEvent.type(await screen.findByLabelText(/Gerekçe/), 'Butcede yeri var');
    await userEvent.click(screen.getByRole('button', { name: 'Onayla' }));

    await waitFor(() => expect(sahte).toHaveBeenCalledTimes(3));

    const [yol, ayarlar] = sahte.mock.calls[1];
    expect(yol).toBe('/api/v1/talepler/6/karar');
    expect(JSON.parse(ayarlar.body)).toEqual({ karar: 'ONAYLA', aciklama: 'Butcede yeri var' });

    expect(await screen.findByText('Onaylandı')).toBeInTheDocument();
  });

  it('amir ikinci kademedeki talebe karar dugmelerini gormez', async () => {
    oturumAc(AMIR);
    fetchSahtele({ govde: YONETICI_ONAYINDA_TALEP });

    detayEkraniniAcId(6);

    expect(await screen.findByText('Yönetici onayında')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Onayla' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reddet' })).not.toBeInTheDocument();
  });

  it('yonetici birinci kademedeki talebe karar dugmelerini gormez', async () => {
    oturumAc(YONETICI);
    fetchSahtele({ govde: BEKLEYEN_TALEP });

    detayEkraniniAc();

    expect(await screen.findByText('Amir onayında')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Onayla' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reddet' })).not.toBeInTheDocument();
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
