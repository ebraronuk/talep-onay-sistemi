import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/istemci';
import type { Bildirim, SayfaYaniti } from '../api/tipler';

export function BildirimlerSayfasi() {
  const [sayfa, setSayfa] = useState<SayfaYaniti<Bildirim> | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  const [yenilemeSayaci, setYenilemeSayaci] = useState(0);

  useEffect(() => {
    let iptal = false;

    api
      .get<SayfaYaniti<Bildirim>>('/bildirimler')
      .then((gelen) => {
        if (iptal) return;
        setSayfa(gelen);
        setHata(null);
      })
      .catch(() => {
        if (!iptal) setHata('Bildirimler yüklenemedi');
      });

    return () => {
      iptal = true;
    };
  }, [yenilemeSayaci]);

  async function okunduIsaretle(id: number) {
    await api.post(`/bildirimler/${id}/okundu`);
    setYenilemeSayaci((n) => n + 1);
  }

  if (hata) return <p role="alert" className="hata-kutusu">{hata}</p>;
  if (!sayfa) return <p>Yükleniyor...</p>;

  return (
    <section>
      <h1>Bildirimler</h1>
      {sayfa.icerik.length === 0 && <p className="bos-durum">Bildiriminiz yok.</p>}
      <ul className="bildirim-listesi">
        {sayfa.icerik.map((bildirim) => (
          <li key={bildirim.id} className={bildirim.okundu ? 'okundu' : 'okunmadi'}>
            <div>
              <p>{bildirim.mesaj}</p>
              <time>{new Date(bildirim.olusturmaTarihi).toLocaleString('tr-TR')}</time>
            </div>
            <div className="bildirim-islem">
              <Link to={`/talepler/${bildirim.talepId}`}>Talebi aç</Link>
              {!bildirim.okundu && (
                <button type="button" className="dugme-ikincil" onClick={() => okunduIsaretle(bildirim.id)}>
                  Okundu
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
