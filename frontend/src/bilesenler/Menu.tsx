import { NavLink, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useOturum } from '../kimlik/useOturum';
import { api } from '../api/istemci';

export function Menu() {
  const { kullanici, cikisYap, rolVarMi } = useOturum();
  const navigate = useNavigate();
  const [okunmamis, setOkunmamis] = useState(0);

  useEffect(() => {
    if (!kullanici) return;
    // Rozet sayisini bir kez cekiyoruz. Canli guncelleme icin websocket ya da
    // periyodik sorgu gerekirdi; ikisi de bu projenin kapsaminda degil.
    api
      .get<{ adet: number }>('/bildirimler/okunmamis-sayisi')
      .then((y) => setOkunmamis(y.adet))
      .catch(() => setOkunmamis(0));
  }, [kullanici]);

  if (!kullanici) return null;

  return (
    <>
      <a className="icerige-atla" href="#ana-icerik">
        İçeriğe atla
      </a>
      <header className="menu">
      <div className="menu-sol">
        <span className="marka">Talep ve Onay Sistemi</span>
        <nav>
          <NavLink to="/talepler">Talepler</NavLink>
          {rolVarMi('PERSONEL') && <NavLink to="/talepler/yeni">Yeni talep</NavLink>}
          {rolVarMi('YONETICI') && <NavLink to="/rapor">Rapor</NavLink>}
          <NavLink to="/bildirimler">
            Bildirimler
            {okunmamis > 0 && (
              <>
                <span className="rozet-sayi" aria-hidden="true">
                  {okunmamis}
                </span>
                {/* Rozetteki cikplak sayi ekran okuyucuda anlamsiz duyuluyor */}
                <span className="gorsel-gizli">{okunmamis} okunmamış bildirim</span>
              </>
            )}
          </NavLink>
        </nav>
      </div>
      <div className="menu-sag">
        <span className="kullanici-bilgi">
          {kullanici.adSoyad}
          <small>
            {kullanici.rol} · {kullanici.birimKodu}
          </small>
        </span>
        <button
          type="button"
          className="dugme-ikincil"
          onClick={() => {
            cikisYap();
            navigate('/giris');
          }}
        >
          Çıkış
        </button>
      </div>
      </header>
    </>
  );
}
