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
