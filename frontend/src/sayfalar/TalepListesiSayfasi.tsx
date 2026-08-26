import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/istemci';
import { DurumRozeti } from '../bilesenler/DurumRozeti';
import { DURUM_ETIKETLERI, TUR_ETIKETLERI } from '../api/tipler';
import type { SayfaYaniti, TalepDurumu, TalepOzeti } from '../api/tipler';
import { useOturum } from '../kimlik/useOturum';

const SAYFA_BOYUTU = 10;

export function TalepListesiSayfasi() {
  const { kullanici } = useOturum();
  const [sayfa, setSayfa] = useState<SayfaYaniti<TalepOzeti> | null>(null);
  const [sayfaNo, setSayfaNo] = useState(0);
  const [durumFiltresi, setDurumFiltresi] = useState<TalepDurumu | ''>('');
  // Ayri ayri "yukleniyor" ve "hata" bayragi yerine tek bir durum: ikisi ayni anda
  // dogru olamaz. Ayrica efektin icinde senkron setState cagirmiyoruz; ilk durum
  // degisikligi await'ten sonra oluyor. Boylece filtre degistiginde ekran bosalip
  // sonra dolmuyor, mevcut liste yenisi gelene kadar duruyor.
  const [durum, setDurum] = useState<'yukleniyor' | 'hazir' | 'hata'>('yukleniyor');

  // Veri cekme dogrudan efektin icinde ve bir iptal bayragiyla.
  //
  // Bu yalnizca lint kurali icin degil: kullanici filtreyi hizli hizli degistirirse
  // istekler yola cikis sirasiyla ayni sirada donmeyebiliyor. Iptal bayragi olmadan
  // eski bir yanit yenisinin uzerine yazip ekranda yanlis liste birakiyor.
  // Temizlik fonksiyonu, efekt yeniden calismadan once eskisini gecersiz kiliyor.
  useEffect(() => {
    let iptal = false;

    const parametreler = new URLSearchParams({
      page: String(sayfaNo),
      size: String(SAYFA_BOYUTU),
    });
    if (durumFiltresi) parametreler.set('durum', durumFiltresi);

    api
      .get<SayfaYaniti<TalepOzeti>>(`/talepler?${parametreler}`)
      .then((gelen) => {
        if (iptal) return;
        setSayfa(gelen);
        setDurum('hazir');
      })
      .catch(() => {
        if (!iptal) setDurum('hata');
      });

    return () => {
      iptal = true;
    };
  }, [sayfaNo, durumFiltresi]);

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

      {durum === 'hata' && <p role="alert" className="hata-kutusu">Talepler yüklenemedi</p>}
      {durum === 'yukleniyor' && (
        <p role="status" aria-live="polite">
          Yükleniyor...
        </p>
      )}

      {durum === 'hazir' && sayfa && sayfa.icerik.length === 0 && (
        <p className="bos-durum">Bu filtreye uyan talep yok.</p>
      )}

      {durum === 'hazir' && sayfa && sayfa.icerik.length > 0 && (
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
                {/* data-etiket: mobilde thead gizleniyor, hucre basligi buradan yaziliyor */}
                <td data-etiket="Başlık">
                  <Link to={`/talepler/${talep.id}`}>{talep.baslik}</Link>
                </td>
                <td data-etiket="Tür">{TUR_ETIKETLERI[talep.tur]}</td>
                <td data-etiket="Talep eden">{talep.talepEdenAdSoyad}</td>
                <td data-etiket="Birim">{talep.birimKodu}</td>
                <td data-etiket="Durum">
                  <DurumRozeti durum={talep.durum} />
                </td>
                <td data-etiket="Tarih">
                  {new Date(talep.olusturmaTarihi).toLocaleDateString('tr-TR')}
                </td>
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
