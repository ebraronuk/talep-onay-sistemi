import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api/istemci';
import { oturumDeposu, OTURUM_DUSTU } from './oturumDeposu';
import { OturumBaglami, type OturumDurumu } from './oturumBaglamiTanimi';
import type { GirisYaniti, KullaniciOzeti, Rol } from '../api/tipler';

/**
 * Oturum durumu.
 *
 * Redux yok: uygulamada paylasilan tek durum "kim giris yapmis" bilgisi.
 * Bunun icin Context fazlasiyla yeterli. Ekranlar arasi paylasilan baska bir
 * durum cikarsa (ornegin filtre tercihleri) karar yeniden gozden gecirilir.
 *
 * Token'in nerede saklandigi ve neden sessionStorage secildigi icin
 * bkz. oturumDeposu.ts ve docs/decisions.md K-012.
 */
export function OturumSaglayici({ children }: { children: ReactNode }) {
  const [kullanici, setKullanici] = useState<KullaniciOzeti | null>(() => oturumDeposu.kullanici());

  const cikisYap = useCallback(() => {
    oturumDeposu.temizle();
    setKullanici(null);
  }, []);

  // Token suresi dolup herhangi bir istek 401 yerse istemci bu olayi yayinliyor.
  useEffect(() => {
    const dinleyici = () => setKullanici(null);
    window.addEventListener(OTURUM_DUSTU, dinleyici);
    return () => window.removeEventListener(OTURUM_DUSTU, dinleyici);
  }, []);

  const girisYap = useCallback(async (kullaniciAdi: string, sifre: string) => {
    const yanit = await api.post<GirisYaniti>('/kimlik/giris', { kullaniciAdi, sifre });
    oturumDeposu.kaydet(yanit.token, yanit.kullanici);
    setKullanici(yanit.kullanici);
  }, []);

  const deger = useMemo<OturumDurumu>(
    () => ({
      kullanici,
      girisYap,
      cikisYap,
      rolVarMi: (...roller: Rol[]) => (kullanici ? roller.includes(kullanici.rol) : false),
    }),
    [kullanici, girisYap, cikisYap],
  );

  return <OturumBaglami.Provider value={deger}>{children}</OturumBaglami.Provider>;
}
