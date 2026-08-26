#!/usr/bin/env node
/*
 * Basit yuk testi. Harici bagimlilik yok, dogrudan `node` ile calisir.
 *
 * Kullanim:
 *   node scripts/yuk-testi.mjs                       # varsayilan: 50 es zamanli, 1000 istek
 *   node scripts/yuk-testi.mjs --esZamanli 100 --istek 2000
 *
 * Neden JMeter/Gatling degil: bu olcumun amaci uctan uca gecikmenin buyuklugunu
 * gormek ve README'ye durust bir sayi yazmak. Bunun icin ayri bir arac kurmak,
 * ogrenmek ve depoya senaryo dosyasi eklemek gereksiz agirlik. Ihtiyac
 * karmasiklasirsa (rampali yuk, farkli senaryolar) Gatling'e gecmek mantikli olur.
 */

const ayar = {
  taban: process.env.TABAN ?? 'http://localhost:8080',
  esZamanli: Number(bayrak('--esZamanli') ?? 50),
  istek: Number(bayrak('--istek') ?? 1000),
  isinma: Number(bayrak('--isinma') ?? 100),
};

function bayrak(ad) {
  const i = process.argv.indexOf(ad);
  return i === -1 ? undefined : process.argv[i + 1];
}

async function girisYap(kullaniciAdi, sifre = 'Parola123!') {
  const yanit = await fetch(`${ayar.taban}/api/v1/kimlik/giris`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ kullaniciAdi, sifre }),
  });
  if (!yanit.ok) {
    throw new Error(`Giris basarisiz (${yanit.status}). Uygulama demo profiliyle calisiyor mu?`);
  }
  return (await yanit.json()).token;
}

function yuzdelik(siraliDizi, oran) {
  if (siraliDizi.length === 0) return 0;
  const indeks = Math.min(siraliDizi.length - 1, Math.ceil(oran * siraliDizi.length) - 1);
  return siraliDizi[Math.max(0, indeks)];
}

async function kosu(ad, istekUret, toplamIstek, esZamanli) {
  const gecikmeler = [];
  const hatalar = new Map();
  let sonraki = 0;

  const baslangic = performance.now();

  async function isci() {
    while (true) {
      const sira = sonraki++;
      if (sira >= toplamIstek) return;

      const t0 = performance.now();
      try {
        const yanit = await istekUret(sira);
        const sure = performance.now() - t0;
        if (yanit.ok) {
          gecikmeler.push(sure);
        } else {
          hatalar.set(yanit.status, (hatalar.get(yanit.status) ?? 0) + 1);
        }
        // Govdeyi tuketmezsek baglanti havuzda asili kalabiliyor.
        await yanit.arrayBuffer();
      } catch (e) {
        hatalar.set(e.message, (hatalar.get(e.message) ?? 0) + 1);
      }
    }
  }

  await Promise.all(Array.from({ length: esZamanli }, isci));

  const gecenSaniye = (performance.now() - baslangic) / 1000;
  gecikmeler.sort((a, b) => a - b);

  return {
    ad,
    basarili: gecikmeler.length,
    hatalar,
    saniyedeIstek: gecikmeler.length / gecenSaniye,
    p50: yuzdelik(gecikmeler, 0.5),
    p95: yuzdelik(gecikmeler, 0.95),
    p99: yuzdelik(gecikmeler, 0.99),
    enKotu: gecikmeler.at(-1) ?? 0,
  };
}

function yazdir(sonuc) {
  const ms = (x) => `${x.toFixed(1)} ms`;
  console.log(`\n### ${sonuc.ad}`);
  console.log(`basarili istek : ${sonuc.basarili}`);
  console.log(`saniyede istek : ${sonuc.saniyedeIstek.toFixed(0)}`);
  console.log(`p50            : ${ms(sonuc.p50)}`);
  console.log(`p95            : ${ms(sonuc.p95)}`);
  console.log(`p99            : ${ms(sonuc.p99)}`);
  console.log(`en kotu        : ${ms(sonuc.enKotu)}`);
  if (sonuc.hatalar.size > 0) {
    console.log(`hatalar        : ${[...sonuc.hatalar].map(([k, v]) => `${k} x${v}`).join(', ')}`);
  }
}

const personelToken = await girisYap('ayse.yilmaz');
const yoneticiToken = await girisYap('hakan.ozturk');

const kayitSayisi = await fetch(`${ayar.taban}/api/v1/raporlar/ozet`, {
  headers: { Authorization: `Bearer ${yoneticiToken}` },
})
  .then((y) => y.json())
  .then((r) => r.toplamTalep);

console.log(`Hedef      : ${ayar.taban}`);
console.log(`Veri       : ${kayitSayisi} talep`);
console.log(`Es zamanli : ${ayar.esZamanli}`);
console.log(`Istek      : ${ayar.istek} (+ ${ayar.isinma} isinma)`);

// Isinma: JIT derlemesi, baglanti havuzu ve sorgu plani onbellegi otursun.
await kosu(
  'isinma',
  () =>
    fetch(`${ayar.taban}/api/v1/talepler?page=0&size=20`, {
      headers: { Authorization: `Bearer ${yoneticiToken}` },
    }),
  ayar.isinma,
  ayar.esZamanli,
);

const okuma = await kosu(
  'Okuma: GET /api/v1/talepler (sayfali liste, 20 kayit)',
  (sira) =>
    fetch(`${ayar.taban}/api/v1/talepler?page=${sira % 5}&size=20`, {
      headers: { Authorization: `Bearer ${yoneticiToken}` },
    }),
  ayar.istek,
  ayar.esZamanli,
);
yazdir(okuma);

const yazma = await kosu(
  'Yazma: POST /api/v1/talepler',
  (sira) =>
    fetch(`${ayar.taban}/api/v1/talepler`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${personelToken}` },
      body: JSON.stringify({
        baslik: `Yuk testi talebi ${sira}`,
        aciklama: 'Yuk testi sirasinda uretilen talep kaydi.',
        tur: 'DIGER',
      }),
    }),
  Math.min(ayar.istek, 300),
  ayar.esZamanli,
);
yazdir(yazma);

const hedefler = [
  ['okuma p95 < 200 ms', okuma.p95 < 200],
  ['yazma p95 < 400 ms', yazma.p95 < 400],
  ['hatasiz', okuma.hatalar.size === 0 && yazma.hatalar.size === 0],
];

console.log('\n### Kabul kriterleri');
hedefler.forEach(([ad, gecti]) => console.log(`${gecti ? 'GECTI ' : 'KALDI '} ${ad}`));

process.exit(hedefler.every(([, gecti]) => gecti) ? 0 : 1);
