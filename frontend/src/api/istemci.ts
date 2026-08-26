import { oturumDeposu, OTURUM_DUSTU } from '../kimlik/oturumDeposu';
import type { HataYaniti } from './tipler';

const TABAN = import.meta.env.VITE_API_TABANI ?? '/api';

/**
 * Arka uctan gelen hatalari tasiyan istisna.
 * Ekranlar `hata.detaylar` uzerinden alan bazli mesajlari gosterebiliyor.
 */
export class ApiHatasi extends Error {
  // Alanlar acikca tanimli: tsconfig'de erasableSyntaxOnly acik oldugu icin
  // yapicida kisayol alan tanimi (parameter property) kullanilamiyor.
  readonly durum: number;
  readonly kod: string;
  readonly detaylar: { alan: string; mesaj: string }[];

  constructor(durum: number, kod: string, mesaj: string, detaylar: { alan: string; mesaj: string }[] = []) {
    super(mesaj);
    this.name = 'ApiHatasi';
    this.durum = durum;
    this.kod = kod;
    this.detaylar = detaylar;
  }
}

async function istek<T>(yol: string, ayarlar: RequestInit = {}): Promise<T> {
  // Token istek aninda okunuyor; bkz. oturumDeposu icindeki not.
  const token = oturumDeposu.token();

  const yanit = await fetch(`${TABAN}${yol}`, {
    ...ayarlar,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...ayarlar.headers,
    },
  });

  // 401: token yok, bozuk ya da suresi dolmus. Oturumu tek yerde dusuruyoruz;
  // her ekranda ayri ayri ele almak "bir yerde unutulmus" riski demek.
  // Giris ucunun kendisi haric: orada 401 zaten "sifre yanlis" demek, oturum
  // dusurulecek bir sey yok ve kullaniciya dogru mesaji gostermek istiyoruz.
  if (yanit.status === 401 && !yol.startsWith('/kimlik/giris')) {
    oturumDeposu.temizle();
    window.dispatchEvent(new Event(OTURUM_DUSTU));
  }

  if (yanit.status === 204) {
    return undefined as T;
  }

  const govde = await yanit.text();
  const veri = govde ? JSON.parse(govde) : null;

  if (!yanit.ok) {
    const hata = veri as HataYaniti | null;
    throw new ApiHatasi(
      yanit.status,
      hata?.kod ?? 'BILINMEYEN_HATA',
      hata?.mesaj ?? 'Beklenmeyen bir hata olustu',
      hata?.detaylar ?? [],
    );
  }

  return veri as T;
}

export const api = {
  get: <T>(yol: string) => istek<T>(yol),
  post: <T>(yol: string, govde?: unknown) =>
    istek<T>(yol, { method: 'POST', body: govde === undefined ? undefined : JSON.stringify(govde) }),
  put: <T>(yol: string, govde: unknown) =>
    istek<T>(yol, { method: 'PUT', body: JSON.stringify(govde) }),
};
