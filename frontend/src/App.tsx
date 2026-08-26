import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { OturumSaglayici } from './kimlik/OturumBaglami';
import { KorumaliYol } from './bilesenler/KorumaliYol';
import { Menu } from './bilesenler/Menu';
import { GirisSayfasi } from './sayfalar/GirisSayfasi';
import { TalepListesiSayfasi } from './sayfalar/TalepListesiSayfasi';
import { TalepDetaySayfasi } from './sayfalar/TalepDetaySayfasi';
import { YeniTalepSayfasi } from './sayfalar/YeniTalepSayfasi';
import { RaporSayfasi } from './sayfalar/RaporSayfasi';
import { BildirimlerSayfasi } from './sayfalar/BildirimlerSayfasi';

export function App() {
  return (
    <OturumSaglayici>
      <BrowserRouter>
        <Menu />
        <main className="icerik" id="ana-icerik">
          <Routes>
            <Route path="/giris" element={<GirisSayfasi />} />

            <Route
              path="/talepler"
              element={
                <KorumaliYol>
                  <TalepListesiSayfasi />
                </KorumaliYol>
              }
            />
            {/* Sira onemli: /talepler/yeni, /talepler/:id'den once gelmeli,
                yoksa "yeni" bir id sanilir. */}
            <Route
              path="/talepler/yeni"
              element={
                <KorumaliYol roller={['PERSONEL']}>
                  <YeniTalepSayfasi />
                </KorumaliYol>
              }
            />
            <Route
              path="/talepler/:id"
              element={
                <KorumaliYol>
                  <TalepDetaySayfasi />
                </KorumaliYol>
              }
            />
            <Route
              path="/rapor"
              element={
                <KorumaliYol roller={['YONETICI']}>
                  <RaporSayfasi />
                </KorumaliYol>
              }
            />
            <Route
              path="/bildirimler"
              element={
                <KorumaliYol>
                  <BildirimlerSayfasi />
                </KorumaliYol>
              }
            />

            <Route path="*" element={<Navigate to="/talepler" replace />} />
          </Routes>
        </main>
      </BrowserRouter>
    </OturumSaglayici>
  );
}
