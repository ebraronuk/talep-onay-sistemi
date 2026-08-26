import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiHatasi } from '../api/istemci';
import { DurumRozeti } from '../bilesenler/DurumRozeti';
import { DURUM_ETIKETLERI, TUR_ETIKETLERI } from '../api/tipler';
import type { TalepDetayi } from '../api/tipler';
import { useOturum } from '../kimlik/OturumBaglami';

export function TalepDetaySayfasi() {
  const { id } = useParams<{ id: string }>();
  const { kullanici } = useOturum();

  const [talep, setTalep] = useState<TalepDetayi | null>(null);
  const [hata, setHata] = useState<string | null>(null);
  const [gerekce, setGerekce] = useState('');
  const [islemde, setIslemde] = useState(false);

  const getir = useCallback(async () => {
    setHata(null);
    try {
      setTalep(await api.get<TalepDetayi>(`/talepler/${id}`));
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Talep yüklenemedi');
    }
  }, [id]);

  useEffect(() => {
    void getir();
  }, [getir]);

  async function islemYap(calistir: () => Promise<TalepDetayi | void>) {
    setIslemde(true);
    setHata(null);
    try {
      await calistir();
      await getir();
      setGerekce('');
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'İşlem yapılamadı');
    } finally {
      setIslemde(false);
    }
  }

  if (hata && !talep) return <p role="alert" className="hata-kutusu">{hata}</p>;
  if (!talep) return <p>Yükleniyor...</p>;

  const sahibiyim = kullanici?.id === talep.talepEden.id;
  const onayaGonderebilirim = sahibiyim && talep.durum === 'TASLAK';
  // Amir kendi talebine karar veremiyor; dugmeyi gostermenin anlami yok.
  const kararVerebilirim =
    kullanici?.rol === 'AMIR' && talep.durum === 'BEKLEMEDE' && !sahibiyim;

  return (
    <section className="detay">
      <div className="sayfa-basligi">
        <h1>{talep.baslik}</h1>
        <DurumRozeti durum={talep.durum} />
      </div>

      <div className="kart">
        <dl className="ozellik-listesi">
          <div>
            <dt>Tür</dt>
            <dd>{TUR_ETIKETLERI[talep.tur]}</dd>
          </div>
          <div>
            <dt>Talep eden</dt>
            <dd>{talep.talepEden.adSoyad}</dd>
          </div>
          <div>
            <dt>Birim</dt>
            <dd>
              {talep.birimAdi} ({talep.birimKodu})
            </dd>
          </div>
          <div>
            <dt>Oluşturma</dt>
            <dd>{new Date(talep.olusturmaTarihi).toLocaleString('tr-TR')}</dd>
          </div>
        </dl>
        <p className="aciklama">{talep.aciklama}</p>
      </div>

      {hata && <p role="alert" className="hata-kutusu">{hata}</p>}

      {(onayaGonderebilirim || kararVerebilirim) && (
        <div className="kart islem-kutusu">
          <h2>İşlem</h2>

          {onayaGonderebilirim && (
            <button
              type="button"
              disabled={islemde}
              onClick={() => islemYap(() => api.post(`/talepler/${talep.id}/onaya-gonder`))}
            >
              Onaya gönder
            </button>
          )}

          {kararVerebilirim && (
            <>
              <label htmlFor="gerekce">Gerekçe (ret için zorunlu)</label>
              <textarea
                id="gerekce"
                value={gerekce}
                onChange={(e) => setGerekce(e.target.value)}
                rows={3}
              />
              <div className="dugme-grubu">
                <button
                  type="button"
                  disabled={islemde}
                  onClick={() =>
                    islemYap(() =>
                      api.post(`/talepler/${talep.id}/karar`, { karar: 'ONAYLA', aciklama: gerekce || null }),
                    )
                  }
                >
                  Onayla
                </button>
                <button
                  type="button"
                  className="dugme-tehlike"
                  disabled={islemde}
                  onClick={() =>
                    islemYap(() =>
                      api.post(`/talepler/${talep.id}/karar`, { karar: 'REDDET', aciklama: gerekce }),
                    )
                  }
                >
                  Reddet
                </button>
              </div>
            </>
          )}
        </div>
      )}

      <div className="kart">
        <h2>Onay geçmişi</h2>
        <ol className="gecmis">
          {talep.gecmis.map((kayit) => (
            <li key={kayit.id}>
              <span className="gecmis-gecis">
                {kayit.oncekiDurum ? DURUM_ETIKETLERI[kayit.oncekiDurum] : 'Oluşturuldu'}
                {kayit.oncekiDurum && ` → ${DURUM_ETIKETLERI[kayit.yeniDurum]}`}
              </span>
              <span className="gecmis-kisi">{kayit.islemYapanAdSoyad}</span>
              <time>{new Date(kayit.islemTarihi).toLocaleString('tr-TR')}</time>
              {kayit.aciklama && <p className="gecmis-aciklama">{kayit.aciklama}</p>}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
