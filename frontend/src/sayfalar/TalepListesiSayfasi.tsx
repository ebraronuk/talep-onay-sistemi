import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/istemci';
import { DurumRozeti } from '../bilesenler/DurumRozeti';
import { DURUM_ETIKETLERI, TUR_ETIKETLERI } from '../api/tipler';
import type { SayfaYaniti, TalepDurumu, TalepOzeti } from '../api/tipler';
import { useOturum } from '../kimlik/OturumBaglami';

const SAYFA_BOYUTU = 10;

export function TalepListesiSayfasi() {
  const { kullanici } = useOturum();
  const [sayfa, setSayfa] = useState<SayfaYaniti<TalepOzeti> | null>(null);
  const [sayfaNo, setSayfaNo] = useState(0);
  const [durumFiltresi, setDurumFiltresi] = useState<TalepDurumu | ''>('');
  const [yukleniyor, setYukleniyor] = useState(true);
  const [hata, setHata] = useState<string | null>(null);

  const getir = useCallback(async () => {
    setYukleniyor(true);
    setHata(null);
    try {
      const parametreler = new URLSearchParams({
        page: String(sayfaNo),
        size: String(SAYFA_BOYUTU),
      });
      if (durumFiltresi) parametreler.set('durum', durumFiltresi);

      setSayfa(await api.get<SayfaYaniti<TalepOzeti>>(`/talepler?${parametreler}`));
    } catch {
      setHata('Talepler yüklenemedi');
    } finally {
      setYukleniyor(false);
    }
  }, [sayfaNo, durumFiltresi]);

  useEffect(() => {
    void getir();
  }, [getir]);

  const baslik =
    kullanici?.rol === 'PERSONEL'
      ? 'Taleplerim'
      : kullanici?.rol === 'AMIR'
        ? `${kullanici.birimKodu} birimi talepleri`
        : 'Tüm talepler';

  return (
    <section>
      <div className="sayfa-basligi">
        <h1>{baslik}</h1>
        <label className="filtre">
          Durum
          <select
            value={durumFiltresi}
            onChange={(e) => {
              setDurumFiltresi(e.target.value as TalepDurumu | '');
              setSayfaNo(0);
            }}
          >
            <option value="">Hepsi</option>
            {(Object.keys(DURUM_ETIKETLERI) as TalepDurumu[]).map((d) => (
              <option key={d} value={d}>
                {DURUM_ETIKETLERI[d]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {hata && <p role="alert" className="hata-kutusu">{hata}</p>}
      {yukleniyor && <p>Yükleniyor...</p>}

      {!yukleniyor && sayfa && sayfa.icerik.length === 0 && (
        <p className="bos-durum">Bu filtreye uyan talep yok.</p>
      )}

      {!yukleniyor && sayfa && sayfa.icerik.length > 0 && (
        <table className="tablo">
          <thead>
            <tr>
              <th>Başlık</th>
              <th>Tür</th>
              <th>Talep eden</th>
              <th>Birim</th>
              <th>Durum</th>
              <th>Tarih</th>
            </tr>
          </thead>
          <tbody>
            {sayfa.icerik.map((talep) => (
              <tr key={talep.id}>
                <td>
                  <Link to={`/talepler/${talep.id}`}>{talep.baslik}</Link>
                </td>
                <td>{TUR_ETIKETLERI[talep.tur]}</td>
                <td>{talep.talepEdenAdSoyad}</td>
                <td>{talep.birimKodu}</td>
                <td>
                  <DurumRozeti durum={talep.durum} />
                </td>
                <td>{new Date(talep.olusturmaTarihi).toLocaleDateString('tr-TR')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {sayfa && sayfa.toplamSayfa > 1 && (
        <div className="sayfalama">
          <button type="button" disabled={sayfaNo === 0} onClick={() => setSayfaNo((n) => n - 1)}>
            Önceki
          </button>
          <span>
            Sayfa {sayfa.sayfaNo + 1} / {sayfa.toplamSayfa} · toplam {sayfa.toplamKayit} kayıt
          </span>
          <button type="button" disabled={sayfa.sonSayfaMi} onClick={() => setSayfaNo((n) => n + 1)}>
            Sonraki
          </button>
        </div>
      )}
    </section>
  );
}
