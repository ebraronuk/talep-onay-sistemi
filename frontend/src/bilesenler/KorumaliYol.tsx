import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useOturum } from '../kimlik/OturumBaglami';
import type { Rol } from '../api/tipler';

/**
 * Giris yapmamis kullaniciyi giris ekranina, yetkisi olmayani listeye yollar.
 *
 * Bu bir guvenlik onlemi DEGIL, kullanici deneyimi onlemi: gercek kontrol
 * arka ucta. Buradaki kontrol yalnizca kullaniciya 403 alacagi bir ekrani
 * bosuna gostermemek icin.
 */
export function KorumaliYol({ children, roller }: { children: ReactNode; roller?: Rol[] }) {
  const { kullanici, rolVarMi } = useOturum();
  const konum = useLocation();

  if (!kullanici) {
    return <Navigate to="/giris" state={{ nereden: konum.pathname }} replace />;
  }

  if (roller && !rolVarMi(...roller)) {
    return <Navigate to="/talepler" replace />;
  }

  return <>{children}</>;
}
