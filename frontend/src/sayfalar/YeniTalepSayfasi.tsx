import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiHatasi } from '../api/istemci';
import { TUR_ETIKETLERI } from '../api/tipler';
import type { TalepDetayi, TalepTuru } from '../api/tipler';

export function YeniTalepSayfasi() {
  const navigate = useNavigate();
  const [baslik, setBaslik] = useState('');
  const [aciklama, setAciklama] = useState('');
  const [tur, setTur] = useState<TalepTuru>('DIGER');
  const [alanHatalari, setAlanHatalari] = useState<Record<string, string>>({});
  const [hata, setHata] = useState<string | null>(null);
  const [gonderiliyor, setGonderiliyor] = useState(false);

  async function gonder(olay: React.FormEvent) {
    olay.preventDefault();
    setHata(null);
    setAlanHatalari({});
    setGonderiliyor(true);
    try {
      const olusan = await api.post<TalepDetayi>('/talepler', { baslik, aciklama, tur });
      navigate(`/talepler/${olusan.id}`);
    } catch (e) {
      if (e instanceof ApiHatasi) {
        // Arka uctan gelen alan bazli dogrulama mesajlarini ilgili alanin altina koyuyoruz.
        // Dogrulama kurallarini on yuzde tekrar yazmiyoruz: iki yerde tutulan kural
        // er ya da gec birbirinden ayrilir.
        setAlanHatalari(Object.fromEntries(e.detaylar.map((d) => [d.alan, d.mesaj])));
        setHata(e.detaylar.length ? null : e.message);
      } else {
        setHata('Talep oluşturulamadı');
      }
    } finally {
      setGonderiliyor(false);
    }
  }

  return (
    <section>
      <h1>Yeni talep</h1>
      <form className="kart" onSubmit={gonder}>
        <label htmlFor="baslik">Başlık</label>
        <input id="baslik" value={baslik} onChange={(e) => setBaslik(e.target.value)} />
        {alanHatalari.baslik && <span className="alan-hatasi">{alanHatalari.baslik}</span>}

        <label htmlFor="tur">Tür</label>
        <select id="tur" value={tur} onChange={(e) => setTur(e.target.value as TalepTuru)}>
          {(Object.keys(TUR_ETIKETLERI) as TalepTuru[]).map((t) => (
            <option key={t} value={t}>
              {TUR_ETIKETLERI[t]}
            </option>
          ))}
        </select>

        <label htmlFor="aciklama">Açıklama</label>
        <textarea
          id="aciklama"
          rows={6}
          value={aciklama}
          onChange={(e) => setAciklama(e.target.value)}
        />
        {alanHatalari.aciklama && <span className="alan-hatasi">{alanHatalari.aciklama}</span>}

        {hata && <p role="alert" className="hata-kutusu">{hata}</p>}

        <button type="submit" disabled={gonderiliyor}>
          {gonderiliyor ? 'Kaydediliyor...' : 'Taslak olarak kaydet'}
        </button>
        <p className="ipucu">
          Talep taslak olarak kaydedilir. Detay ekranından onaya gönderebilirsiniz.
        </p>
      </form>
    </section>
  );
}
