# Performans Ölçümleri

Bu dosyadaki her sayı gerçek bir çalıştırmadan alınmıştır. Tahmin veya hedef değil, ölçüm.

**Ölçüm ortamı:** Apple Silicon (arm64), macOS 26.5, PostgreSQL 16.14 (Docker, `postgres:16-alpine`), Temurin JDK 21.0.12 LTS.

---

## 1. Faz 1: Listeleme sorgusu indeks kullanıyor mu

**Soru.** Amir ekranındaki "bu birimin bekleyen talepleri" sorgusu 50.000 kayıtta sequential scan yapıyor mu?

**Veri.** 20 birim, 200 kullanıcı, 50.000 talep. Durum ve birim dağılımı bilinçli olarak birbirinden bağımsız üretildi (aksi halde planlayıcı korelasyondan yanılıyor). `ANALYZE` çalıştırıldı.

**Sorgu.**

```sql
SELECT t.id, t.baslik, t.durum, t.olusturma_tarihi
FROM talep t
WHERE t.durum = 'BEKLEMEDE' AND t.birim_id = 7
ORDER BY t.olusturma_tarihi DESC
LIMIT 20;
```

### Gerçek plan (ix_talep_durum_birim mevcut)

```
 Limit  (cost=959.35..959.40 rows=20 width=36) (actual time=0.832..0.835 rows=20 loops=1)
   Buffers: shared hit=633
   ->  Sort  (cost=959.35..960.93 rows=631 width=36) (actual time=0.831..0.833 rows=20 loops=1)
         Sort Key: olusturma_tarihi DESC
         Sort Method: top-N heapsort  Memory: 26kB
         Buffers: shared hit=633
         ->  Bitmap Heap Scan on talep t  (cost=10.76..942.56 rows=631 width=36) (actual time=0.149..0.731 rows=625 loops=1)
               Recheck Cond: (((durum)::text = 'BEKLEMEDE'::text) AND (birim_id = 7))
               Heap Blocks: exact=625
               Buffers: shared hit=630
               ->  Bitmap Index Scan on ix_talep_durum_birim  (cost=0.00..10.60 rows=631 width=0) (actual time=0.081..0.081 rows=625 loops=1)
                     Index Cond: (((durum)::text = 'BEKLEMEDE'::text) AND (birim_id = 7))
                     Buffers: shared hit=5
 Planning:
   Buffers: shared hit=149
 Planning Time: 0.505 ms
 Execution Time: 0.869 ms
(17 rows)

```

### Karşılaştırma için indeks kapatılmış hali

`SET enable_indexscan = off; SET enable_bitmapscan = off;` ile planlayıcı sequential scan'e zorlandı:

```
 Limit  (cost=1766.79..1766.84 rows=20 width=36) (actual time=3.132..3.134 rows=20 loops=1)
   Buffers: shared hit=1003
   ->  Sort  (cost=1766.79..1768.37 rows=631 width=36) (actual time=3.131..3.131 rows=20 loops=1)
         Sort Key: olusturma_tarihi DESC
         Sort Method: top-N heapsort  Memory: 26kB
         Buffers: shared hit=1003
         ->  Seq Scan on talep t  (cost=0.00..1750.00 rows=631 width=36) (actual time=0.006..3.063 rows=625 loops=1)
               Filter: (((durum)::text = 'BEKLEMEDE'::text) AND (birim_id = 7))
               Rows Removed by Filter: 49375
               Buffers: shared hit=1000
 Planning:
   Buffers: shared hit=143
 Planning Time: 0.355 ms
 Execution Time: 3.148 ms
(14 rows)

```

### Sonuç

| Ölçüt | İndeksli | İndeks kapalı |
|---|---|---|
| Erişim yöntemi | `Bitmap Index Scan on ix_talep_durum_birim` | `Seq Scan on talep` |
| Okunan blok (buffers) | 633 | 1003 |
| Filtreyle elenen satır | 0 | 49.375 |
| Yürütme süresi | **0,869 ms** | 3,148 ms |

Kabul kriteri (sequential scan olmayacak) **sağlandı**. Sorgu, `ix_talep_durum_birim` bileşik indeksini `Index Cond` içinde her iki kolonuyla birlikte kullanıyor; `Filter` satırında elenen kayıt yok, yani indeks sorgunun tamamını karşılıyor.

**Kolon sırası neden (durum, birim_id).** Her iki kolon da eşitlikle filtrelendiği için sıralama seçiciliğe göre yapıldı. Ters sıra da çalışırdı; ancak `durum` tek başına da sorgulanan bir kolon (yöneticinin "sistemdeki tüm bekleyen talepler" ekranı), `birim_id` tek başına sorgulanmıyor. Bileşik indeks yalnızca soldan başlayan önek için kullanılabildiğinden `durum` başa alındı.

---

## 2. Faz 3: N+1 sorgu problemi, önce ve sonra

**Soru.** 10 talep ilişkileriyle listelendiğinde kaç SQL deyimi çalışıyor?

**Ölçüm yöntemi.** Tahmin değil sayım: Hibernate'in `generate_statistics` özelliği açıldı ve `Statistics.getPrepareStatementCount()` okundu. Ölçümden önce `EntityManager.clear()` çağrılıyor; yoksa varlıklar zaten kalıcılık bağlamında olur ve hiç SELECT çalışmaz, ölçüm anlamsızlaşır.

**Kurulum.** 10 talebin her biri **farklı** bir kullanıcıya ait. Hepsi aynı kullanıcıya ait olsaydı Hibernate ilk yüklemeden sonra kalıcılık bağlamından dönerdi ve problem görünmezdi.

İlgili test: `src/test/java/tr/ebrar/talep/repository/TalepNArtiBirTest.java`

### Ölçüm çıktısı

Testin kendi log satırları (`./mvnw test -Dtest=TalepNArtiBirTest` çalıştırılınca aynen görülür):

```
N+1 OLCUMU (grafiksiz):          liste sorgusu=1, iliskiler icin ek sorgu=10, toplam=11
N+1 OLCUMU (@EntityGraph):       liste sorgusu=1, iliskiler icin ek sorgu=0,  toplam=1
N+1 OLCUMU (Specification+fetch): yukleme=2 (icerik + count), ek sorgu=0
```

| Yaklaşım | Liste sorgusu | İlişkiler için ek sorgu | Toplam |
|---|---|---|---|
| `findAll()`, ilişkiler tembel | 1 | 10 | **11** |
| `@EntityGraph(attributePaths = {"talepEden", "birim"})` | 1 | 0 | **1** |
| `Specification` + `root.fetch(...)`, sayfalı | 2 (içerik + adet) | 0 | **2** |

**Yorum.** Klasik N+1: bir liste sorgusu, sonra her satır için bir sorgu daha. 10 kayıtta fark edilmez, 500 kayıtlık bir raporda uygulamayı durdurur. Çözüm ilişkiyi EAGER yapmak **değil**; EAGER, ilişkiye ihtiyaç duymayan sorguları da yavaşlatır ve sorunu tek bir yere değil her yere yayar. Doğru çözüm, ilişkinin gerektiği sorguda `@EntityGraph` veya `join fetch` ile açıkça istenmesi.

**Sayfalamada dikkat.** `Specification` ile fetch join yapılırken Spring Data ayrıca bir `count` sorgusu çalıştırır. Count sorgusunun sonuç tipi `Long`'dur ve fetch join içeremez. `TalepSpecifications.iliskileriGetir()` bu yüzden `query.getResultType()` kontrolü yapar; kontrol kaldırılırsa sayfalı sorgular çalışma anında hata verir.

---

## 3. Faz 3: 1000 kayıtta sayfalı listeleme süresi

**Kabul kriteri.** 1000 kayıtta 20'lik sayfa 200 ms altında dönmeli.

**Yöntem.** 3 ısınma turu (JIT derlemesi, bağlantı havuzu, plan önbelleği ısınsın), ardından 10 ölçüm turu. Karar medyana göre verildi. Ölçüme ilişkilere erişim de dahil: N+1 olsaydı süre buradan patlardı.

İlgili test: `src/test/java/tr/ebrar/talep/repository/TalepListelemePerformansTest.java`

```
SAYFALAMA OLCUMU: 1000 kayit, sayfa boyutu 20,
medyan=2 ms, en kotu=3 ms, tum olcumler=[2, 2, 2, 2, 2, 2, 2, 2, 2, 3]
```

| Ölçüt | Değer | Sınır | Sonuç |
|---|---|---|---|
| Medyan | 2 ms | 200 ms | Geçti |
| En kötü tur | 3 ms | 200 ms | Geçti |

Sınırın 100 katı altında kalınması sürpriz değil: sorgu `ix_talep_durum_birim` indeksini kullanıyor, sayfa boyutu 20 ve ilişkiler tek sorguda geliyor. Bu ölçümün asıl değeri mutlak sayı değil, **regresyon bekçisi** olması: biri `@EntityGraph`'ı kaldırırsa veya indeksi düşürürse bu test kırmızıya döner.

---

## 4. Faz 4: Test kapsamı (JaCoCo)

`./mvnw clean verify` çıktısındaki `target/site/jacoco/jacoco.csv` dosyasından, paket bazında satır kapsamı:

| Paket | Satır kapsamı |
|---|---|
| `service` | **%96,5** (195/202) |
| `security` | %97,0 (97/100) |
| `repository` | %88,2 (15/17) |
| `web` | %84,7 (61/72) |
| `service.hata` | %88,2 (15/17) |
| `domain` | %75,1 (130/173) |

Kabul kriteri servis katmanı için %70 idi, %96,5 ile sağlandı.

`domain` paketindeki %75, kapsanmayan `equals`/`hashCode`/`toString` dallarından geliyor; bunlar için test yazmak kapsam sayısını süsler ama bir şey kanıtlamaz. `config` paketi düşük çünkü demo veri yükleyici yalnızca `demo` profilinde çalışıyor ve testlerde devrede değil.

**Toplam: 124 arka uç testi, hepsi yeşil.** Ön yüzde ayrıca 15 Vitest testi var.

| Test türü | Adet | Nerede |
|---|---|---|
| Saf birim (Spring yok) | 41 | durum makinesi, servis + Mockito |
| Repository (gerçek PostgreSQL) | 26 | Testcontainers |
| HTTP katmanı (`@WebMvcTest`) | 13 | hata sözleşmesi, status kodları |
| Güvenlik entegrasyonu | 32 | gerçek filtre zinciri |
| Transaction / bildirim | 7 | rollback davranışı, commit sonrası olay |
| Korelasyon kimliği | 4 | istek izlenebilirliği |
| Performans bekçisi | 1 | 1000 kayıtta sayfalama |

---

## 5. Faz 9: Uçtan uca yük testi

**Kabul kriteri.** Okuma p95 < 200 ms, yazma p95 < 400 ms, 50 eşzamanlı istek altında hatasız.

**Yöntem.** `scripts/yuk-testi.mjs`. Harici araç yok, `node scripts/yuk-testi.mjs` ile çalışıyor. 100 istekli ısınma turundan sonra ölçüm alınıyor. Ölçüm HTTP katmanından: JWT doğrulama, yetki kontrolü, sorgu, DTO çevrimi ve JSON serileştirme dahil.

**Ortam.** Apple Silicon (arm64), uygulama yerelde JVM üzerinde, PostgreSQL 16 Docker konteynerinde. 1007 talep kaydı.

```
Es zamanli : 50
Istek      : 1000 (+ 100 isinma)

### Okuma: GET /api/talepler (sayfali liste, 20 kayit)
basarili istek : 1000
saniyede istek : 1505
p50            : 29.9 ms
p95            : 64.2 ms
p99            : 93.9 ms
en kotu        : 143.3 ms

### Yazma: POST /api/talepler
basarili istek : 300
saniyede istek : 1237
p50            : 32.2 ms
p95            : 91.1 ms
p99            : 111.4 ms
en kotu        : 117.1 ms
```

| Ölçüt | Sonuç | Sınır | Durum |
|---|---|---|---|
| Okuma p95 | 64,2 ms | 200 ms | Geçti |
| Okuma p99 | 93,9 ms | - | - |
| Yazma p95 | 91,1 ms | 400 ms | Geçti |
| Hata sayısı | 0 | 0 | Geçti |
| Okuma verimi | 1505 istek/sn | - | - |

### Bellek

| Ölçüt | Değer | Sınır |
|---|---|---|
| JVM heap kullanımı (boşta) | 195 MB | - |
| İşletim sistemi RSS | 331 MB | 512 MB |

`jvm.memory.used` değeri Actuator'ın `/actuator/metrics` ucundan, RSS ise `ps` ile alındı. İkisi arasındaki fark JVM'in kendi metaspace, kod önbelleği ve iş parçacığı yığınları.

### Bu sayılar ne anlatıyor, ne anlatmıyor

**Anlattığı:** Uygulama katmanında bariz bir darboğaz yok. 50 eşzamanlı istek altında ne bağlantı havuzu tükeniyor ne de kuyruk oluşuyor.

**Anlatmadığı:** Bu bir tek makine ölçümü. Gerçek bir üretim ortamında ağ gecikmesi, ayrı bir veritabanı sunucusu, TLS sonlandırma ve yük dengeleyici devrede olur. Bu sayılar tavan değil, taban: gerçek ortamda daha yüksek çıkar.
