import { useEffect, useState } from 'react';
import { api } from '../api/istemci';
import { DURUM_ETIKETLERI } from '../api/tipler';
import type { Rapor } from '../api/tipler';

export function RaporSayfasi() {
  const [rapor, setRapor] = useState<Rapor | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Rapor>('/raporlar/ozet')
      .then(setRapor)
      .catch(() => setHata('Rapor yüklenemedi'));
  }, []);

  if (hata) return <p role="alert" className="hata-kutusu">{hata}</p>;
  if (!rapor) return <p>Yükleniyor...</p>;

  const enBuyuk = Math.max(...rapor.durumDagilimi.map((d) => d.adet), 1);

  return (
    <section>
      <h1>Özet rapor</h1>

      <div className="ozet-kutulari">
        <div className="kart ozet">
          <span className="ozet-sayi">{rapor.toplamTalep}</span>
          <span className="ozet-etiket">toplam talep</span>
        </div>
        <div className="kart ozet">
          <span className="ozet-sayi">{rapor.bekleyenTalep}</span>
          <span className="ozet-etiket">onay bekliyor</span>
        </div>
      </div>

      <div className="kart">
        <h2>Durum dağılımı</h2>
        <ul className="cubuk-grafik">
          {rapor.durumDagilimi.map((satir) => (
            <li key={satir.durum}>
              <span className="cubuk-etiket">{DURUM_ETIKETLERI[satir.durum]}</span>
              <span className="cubuk-yuva">
                <span
                  className={`cubuk cubuk-${satir.durum.toLowerCase()}`}
                  style={{ width: `${(satir.adet / enBuyuk) * 100}%` }}
                />
              </span>
              <span className="cubuk-sayi">{satir.adet}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
