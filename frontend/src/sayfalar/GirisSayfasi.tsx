import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useOturum } from '../kimlik/useOturum';
import { ApiHatasi } from '../api/istemci';

export function GirisSayfasi() {
  const { girisYap } = useOturum();
  const navigate = useNavigate();
  const konum = useLocation();

  const [kullaniciAdi, setKullaniciAdi] = useState('');
  const [sifre, setSifre] = useState('');
  const [hata, setHata] = useState<string | null>(null);
  const [gonderiliyor, setGonderiliyor] = useState(false);

  async function gonder(olay: React.FormEvent) {
    olay.preventDefault();
    setHata(null);
    setGonderiliyor(true);
    try {
      await girisYap(kullaniciAdi, sifre);
      const nereden = (konum.state as { nereden?: string } | null)?.nereden ?? '/talepler';
      navigate(nereden, { replace: true });
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Giriş yapılamadı');
    } finally {
      setGonderiliyor(false);
    }
  }

  return (
    <div className="giris-sayfa">
      <form className="kart giris-kart" onSubmit={gonder}>
        <h1>Talep ve Onay Sistemi</h1>
        <p className="alt-baslik">Kurumsal talep ve onay yönetimi</p>

        <label htmlFor="kullaniciAdi">Kullanıcı adı</label>
        <input
          id="kullaniciAdi"
          value={kullaniciAdi}
          onChange={(e) => setKullaniciAdi(e.target.value)}
          autoComplete="username"
          required
        />

        <label htmlFor="sifre">Şifre</label>
        <input
          id="sifre"
          type="password"
          value={sifre}
          onChange={(e) => setSifre(e.target.value)}
          autoComplete="current-password"
          required
        />

        {hata && <p role="alert" className="hata-kutusu">{hata}</p>}

        <button type="submit" disabled={gonderiliyor}>
          {gonderiliyor ? 'Giriş yapılıyor...' : 'Giriş yap'}
        </button>

        <details className="demo-bilgi">
          <summary>Demo hesapları</summary>
          <ul>
            <li><code>ayse.yilmaz</code> · personel</li>
            <li><code>ali.vural</code> · birim amiri</li>
            <li><code>hakan.ozturk</code> · yönetici</li>
          </ul>
          <p>Hepsinin şifresi: <code>Parola123!</code></p>
        </details>
      </form>
    </div>
  );
}
