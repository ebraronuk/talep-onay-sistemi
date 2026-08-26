import type { KullaniciOzeti } from '../api/tipler';

const TOKEN_ANAHTARI = 'talep-onay-token';
const KULLANICI_ANAHTARI = 'talep-onay-kullanici';

/** Oturum dusunce yayinlanan tarayici olayi. */
export const OTURUM_DUSTU = 'talep-onay:oturum-dustu';

/**
 * Token'in tek kaynagi.
 *
 * Once bunu React state'inde tutup API istemcisine bir efektten baglamistim.
 * Tarayicida su hata cikti: giris basarili oluyor, hemen ardindan menunun
 * attigi ilk istek 401 donuyor ve kullanici giris ekranina geri atiliyordu.
 *
 * Sebep React'in efekt sirasi: cocuk bilesenlerin efektleri ebeveynden ONCE
 * calisiyor. Menu'nun efekti istegi attiginda, token'i baglayan ebeveyn efekti
 * henuz calismamis oluyordu ve istek Authorization basligi olmadan gidiyordu.
 *
 * Cozum, sirayla ilgilenmeyi birakmak: token dogrudan buradan, istek aninda
 * okunuyor. Boylece hangi efektin once calistiginin bir onemi kalmiyor.
 */
export const oturumDeposu = {
  token(): string | null {
    return sessionStorage.getItem(TOKEN_ANAHTARI);
  },

  kullanici(): KullaniciOzeti | null {
    const ham = sessionStorage.getItem(KULLANICI_ANAHTARI);
    return ham ? (JSON.parse(ham) as KullaniciOzeti) : null;
  },

  kaydet(token: string, kullanici: KullaniciOzeti) {
    sessionStorage.setItem(TOKEN_ANAHTARI, token);
    sessionStorage.setItem(KULLANICI_ANAHTARI, JSON.stringify(kullanici));
  },

  temizle() {
    sessionStorage.removeItem(TOKEN_ANAHTARI);
    sessionStorage.removeItem(KULLANICI_ANAHTARI);
  },
};
