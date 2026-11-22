import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { RaporSayfasi } from './RaporSayfasi';
import { ekranaBas, fetchSahtele, oturumAc, YONETICI } from '../test/yardimcilar';

describe('Ozet rapor', () => {
  it('toplamlari gosterir, cubuklari en buyuk degere gore olcekler', async () => {
    oturumAc(YONETICI);
    fetchSahtele({
      govde: {
        birimId: null,
        toplamTalep: 10,
        bekleyenTalep: 4,
        durumDagilimi: [
          { durum: 'BEKLEMEDE', adet: 4 },
          { durum: 'ONAYLANDI', adet: 8 },
        ],
      },
    });

    const { container } = ekranaBas(<RaporSayfasi />);

    expect(await screen.findByRole('heading', { name: 'Özet rapor' })).toBeInTheDocument();
    expect(screen.getByText('toplam talep').previousSibling).toHaveTextContent('10');
    expect(screen.getByText('onay bekliyor').previousSibling).toHaveTextContent('4');

    const cubuklar = [...container.querySelectorAll<HTMLElement>('.cubuk')].map((c) => c.style.width);
    expect(cubuklar).toEqual(['50%', '100%']);
  });

  it('hic talep yokken sifira bolmez', async () => {
    oturumAc(YONETICI);
    fetchSahtele({
      govde: { birimId: null, toplamTalep: 0, bekleyenTalep: 0, durumDagilimi: [{ durum: 'BEKLEMEDE', adet: 0 }] },
    });

    const { container } = ekranaBas(<RaporSayfasi />);

    await screen.findByRole('heading', { name: 'Özet rapor' });
    expect(container.querySelector<HTMLElement>('.cubuk')?.style.width).toBe('0%');
  });

  it('istek basarisiz olursa uyari gosterir', async () => {
    oturumAc(YONETICI);
    fetchSahtele({ durum: 500, govde: { kod: 'SUNUCU_HATASI', mesaj: 'hata' } });

    ekranaBas(<RaporSayfasi />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Rapor yüklenemedi');
  });
});
