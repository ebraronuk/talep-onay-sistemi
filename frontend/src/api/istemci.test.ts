import { describe, expect, it, vi } from 'vitest';
import { api, ApiHatasi } from './istemci';
import { oturumDeposu, OTURUM_DUSTU } from '../kimlik/oturumDeposu';

function fetchSahtele(durum: number, govde: unknown = null) {
  const sahte = vi.fn().mockResolvedValue({
    ok: durum >= 200 && durum < 300,
    status: durum,
    text: async () => (govde === null ? '' : JSON.stringify(govde)),
  });
  vi.stubGlobal('fetch', sahte);
  return sahte;
}

describe('API istemcisi', () => {
  it('token depoda varsa Authorization basligi ekler', async () => {
    // Regresyon testi. Once token React state'inden bir efektle baglaniyordu ve
    // cocuk efektleri ebeveynden once calistigi icin girisin hemen ardindan atilan
    // ilk istek basliksiz gidip 401 yiyordu. Artik token istek aninda okunuyor.
    oturumDeposu.kaydet('jwt-token', {
      id: 1,
      kullaniciAdi: 'ayse',
      adSoyad: 'Ayse',
      rol: 'PERSONEL',
      birimKodu: 'BTGM',
    });

    const sahte = fetchSahtele(200, { icerik: [] });
    await api.get('/talepler');

    expect(sahte.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-token');
  });

  it('token yoksa Authorization basligi eklemez', async () => {
    const sahte = fetchSahtele(200, {});
    await api.get('/talepler');

    expect(sahte.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('401 gelince oturumu dusurur ve olay yayinlar', async () => {
    oturumDeposu.kaydet('eski-token', {
      id: 1,
      kullaniciAdi: 'ayse',
      adSoyad: 'Ayse',
      rol: 'PERSONEL',
      birimKodu: 'BTGM',
    });
    fetchSahtele(401, { kod: 'KIMLIK_DOGRULANAMADI', mesaj: 'token gecersiz', detaylar: [] });

    const dinleyici = vi.fn();
    window.addEventListener(OTURUM_DUSTU, dinleyici);

    await expect(api.get('/talepler')).rejects.toBeInstanceOf(ApiHatasi);

    expect(dinleyici).toHaveBeenCalled();
    expect(oturumDeposu.token()).toBeNull();
    window.removeEventListener(OTURUM_DUSTU, dinleyici);
  });

  it('giris ucundeki 401 oturumu dusurmez', async () => {
    fetchSahtele(401, { kod: 'KIMLIK_DOGRULANAMADI', mesaj: 'Kullanici adi veya sifre hatali', detaylar: [] });

    const dinleyici = vi.fn();
    window.addEventListener(OTURUM_DUSTU, dinleyici);

    await expect(api.post('/kimlik/giris', {})).rejects.toThrow('Kullanici adi veya sifre hatali');

    expect(dinleyici).not.toHaveBeenCalled();
    window.removeEventListener(OTURUM_DUSTU, dinleyici);
  });

  it('204 yanitinda govde okumaya calismaz', async () => {
    fetchSahtele(204);
    await expect(api.post('/bildirimler/1/okundu')).resolves.toBeUndefined();
  });
});
