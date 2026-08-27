import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { YeniTalepSayfasi } from './YeniTalepSayfasi';
import { ekranaBas, fetchSahtele, oturumAc, PERSONEL } from '../test/yardimcilar';

describe('Talep olusturma akisi', () => {
  it('formu doldurup gonderince dogru govde ile POST atar', async () => {
    oturumAc(PERSONEL);
    const sahte = fetchSahtele({ durum: 201, govde: { id: 42 } });

    ekranaBas(<YeniTalepSayfasi />);

    await userEvent.type(screen.getByLabelText('Başlık'), 'Ergonomik sandalye');
    await userEvent.selectOptions(screen.getByLabelText('Tür'), 'DONANIM');
    await userEvent.type(screen.getByLabelText(/Tutar/), '1500.5');
    await userEvent.type(screen.getByLabelText('Açıklama'), 'Bel agrisi nedeniyle');
    await userEvent.click(screen.getByRole('button', { name: 'Taslak olarak kaydet' }));

    await waitFor(() => expect(sahte).toHaveBeenCalled());

    const [yol, ayarlar] = sahte.mock.calls[0];
    expect(yol).toBe('/api/v1/talepler');
    expect(ayarlar.method).toBe('POST');
    expect(ayarlar.headers.Authorization).toBe('Bearer test-token');
    expect(JSON.parse(ayarlar.body)).toEqual({
      baslik: 'Ergonomik sandalye',
      aciklama: 'Bel agrisi nedeniyle',
      tur: 'DONANIM',
      tutar: 1500.5,
    });
  });

  it('tutar bos birakilinca govdede null gonderilir', async () => {
    oturumAc(PERSONEL);
    const sahte = fetchSahtele({ durum: 201, govde: { id: 43 } });

    ekranaBas(<YeniTalepSayfasi />);

    await userEvent.type(screen.getByLabelText('Başlık'), 'Yillik izin');
    await userEvent.selectOptions(screen.getByLabelText('Tür'), 'IZIN');
    await userEvent.type(screen.getByLabelText('Açıklama'), '15-22 Eylul arasi');
    await userEvent.click(screen.getByRole('button', { name: 'Taslak olarak kaydet' }));

    await waitFor(() => expect(sahte).toHaveBeenCalled());

    expect(JSON.parse(sahte.mock.calls[0][1].body)).toEqual({
      baslik: 'Yillik izin',
      aciklama: '15-22 Eylul arasi',
      tur: 'IZIN',
      tutar: null,
    });
  });

  it('arka uctan gelen alan hatalarini ilgili alanin altinda gosterir', async () => {
    oturumAc(PERSONEL);
    fetchSahtele({
      durum: 400,
      govde: {
        kod: 'DOGRULAMA_HATASI',
        mesaj: 'Gonderilen veri gecerli degil',
        detaylar: [{ alan: 'baslik', mesaj: 'Baslik zorunludur' }],
      },
    });

    ekranaBas(<YeniTalepSayfasi />);

    await userEvent.type(screen.getByLabelText('Açıklama'), 'aciklama');
    await userEvent.click(screen.getByRole('button', { name: 'Taslak olarak kaydet' }));

    expect(await screen.findByText('Baslik zorunludur')).toBeInTheDocument();
  });
});
