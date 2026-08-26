import { useContext } from 'react';
import { OturumBaglami } from './oturumBaglamiTanimi';

/** Oturum bilgisine erisim. Saglayici disinda cagrilirsa erken ve net hata verir. */
export function useOturum() {
  const baglam = useContext(OturumBaglami);
  if (!baglam) {
    throw new Error('useOturum yalnizca OturumSaglayici icinde kullanilabilir');
  }
  return baglam;
}
