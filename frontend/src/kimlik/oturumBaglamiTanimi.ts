import { createContext } from 'react';
import type { KullaniciOzeti, Rol } from '../api/tipler';

export interface OturumDurumu {
  kullanici: KullaniciOzeti | null;
  girisYap: (kullaniciAdi: string, sifre: string) => Promise<void>;
  cikisYap: () => void;
  rolVarMi: (...roller: Rol[]) => boolean;
}

/**
 * Baglam tanimi bilesenden ayri dosyada.
 *
 * React Fast Refresh yalnizca bir dosya sadece bilesen disa aciyorsa calisiyor.
 * Baglam ve saglayici ayni dosyadayken her degisiklikte tam sayfa yenileniyordu.
 */
export const OturumBaglami = createContext<OturumDurumu | null>(null);
