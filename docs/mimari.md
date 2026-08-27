# Mimari, Nesne Tasarımı ve Kod Standartları

Bu dosya "neden böyle yazıldı" sorusunun cevabı. Her iddianın karşısında kodda nereye bakılacağı yazılı; iddiaların çoğu ayrıca teste bağlı.

---

## 1. Katman mimarisi

```
web  ──►  service  ──►  repository  ──►  domain
 │           │              │              ▲
 │           └──────────────┴──────────────┘
 └──► hata  ◄── security
```

Bağımlılık **tek yönlü**. `domain` en içte ve hiçbir üst katmanı tanımıyor; `web` en dışta.

| Katman | Sorumluluk | Ne yapmaz |
|---|---|---|
| `domain` | Varlıklar, durum makinesi, değişmezler (invariant) | HTTP, Spring Web, veri erişimi bilmez |
| `repository` | Veri erişimi, sorgu kurma, `Specification` | İş kuralı barındırmaz |
| `service` | İş kuralları, transaction sınırları, yetki (kayıt bazlı) | HTTP ve kalıcılık detayı bilmez |
| `web` | HTTP ile servis arasında çeviri, hata sözleşmesi | İş mantığı barındırmaz, repository'ye dokunmaz |
| `security` | Kimlik doğrulama, rol kontrolü | İş kuralı barındırmaz |
| `hata` | Alan diline ait istisna hiyerarşisi ve hata sözleşmesi | Hiçbir şeye bağımlı değil |

### Bunlar yorum değil, test

`src/test/java/tr/ebrar/talep/mimari/MimariKurallariTest.java` içinde ArchUnit ile on kural yazılı. README'de "controller'da iş mantığı yok" yazmak kolay; altı ay sonra hâlâ doğru olmasını sağlayan şey bu testler.

| Kural | Gerekçe |
|---|---|
| `domain` üst katmanları tanımaz | İş kurallarını test etmek için çerçeve ayağa kaldırmak gerekmesin |
| `domain` Spring Web ve Security'ye bağımlı değil | Alan modeli HTTP'den bağımsız olmalı |
| `web` repository'ye dokunmaz | Doğrudan erişim transaction sınırını ve yetki kontrolünü atlar |
| `repository` yukarı bakmaz | Bağımlılık yönü tek yönlü kalsın |
| `service` web'i tanımaz | İş kuralları HTTP'den bağımsız olmalı |
| `service` `Specification` / Criteria API kullanmaz | Kalıcılık detayı iş katmanına sızmasın |
| Controller metotları entity dönmez | Şema, API sözleşmesine dönüşmesin; lazy ilişki serileştirmede patlamasın |
| Paketler arasında döngü yok | Döngü, katman ayrımının kâğıtta kaldığının işareti |
| `System.out` kullanılmaz | Loglama SLF4J üzerinden, korelasyon kimliğiyle |
| Repository arayüzleri `repository` paketinde | Veri erişimi tek yerde |

**Bu testler gerçek iki hata yakaladı** (yazıldıkları gün):

1. `Talep` (domain) → `GecersizDurumGecisiException` (hata) → `TalepDurumu` (domain) döngüsü. İstisna domain paketine taşındı.
2. `security` → `web` (`HataYaniti`) → `service` → `security` döngüsü. `HataYaniti` nötr `hata` paketine taşındı.

İkisi de derlenen, testleri geçen koddu. ArchUnit olmasa fark edilmezdi.

---

## 2. Clean Architecture: ne alındı, ne alınmadı

Clean Architecture'ın (ve Hexagonal/Ports & Adapters'ın) özü tek bir kural: **bağımlılıklar içeri doğru akar.** İş kuralları, kendilerini çağıran ve kendilerine veri getiren şeyleri bilmez.

### Alınanlar

| İlke | Bu projede karşılığı |
|---|---|
| Bağımlılık yönü içeri | `domain` hiçbir üst katmanı tanımıyor, ArchUnit ile zorlanıyor |
| İş kuralları çerçeveden bağımsız | `TalepDurumu` ve `Talep.durumDegistir` saf Java; testi milisaniyelerde koşuyor |
| Girdi/çıktı sınırları açık | Girdi `komut` record'ları, çıktı `dto` record'ları; entity dışarı çıkmıyor |
| Detaylar dışarıda | Veritabanı, HTTP, JWT hepsi dış halkada |

### Bilinçli olarak alınmayanlar

**Repository için kendi port arayüzümüzü tanımlamadık.** Saf Clean Architecture'da `domain` kendi `TalepDeposu` arayüzünü tanımlar, `repository` katmanı onu implemente eder. Yapmadık çünkü:

- Spring Data'nın `JpaRepository`'si zaten bir arayüz; kendi arayüzümüz onun üzerine yalnızca bir delegasyon katmanı eklerdi.
- Bu soyutlamanın karşılığını almak için veritabanını değiştirmek gerekir. Bu proje için gerçekçi değil ve "belki lazım olur" diye soyutlama eklemek tam da kaçındığımız şey.
- Bağımlılık yönü zaten korunuyor: `domain` repository'yi tanımıyor, `service` tanıyor.

Bedeli dürüstçe: `service`, `org.springframework.data.domain.Page` ve `Pageable` tiplerini kullanıyor. Bu bir çerçeve sızıntısı. Sayfalama için kendi tipimizi tanımlamak mümkündü ama her sınırda çeviri yazmak gerekirdi. Bunun yerine daha derin sızıntı olan `Specification`'ı kesip repository'ye taşıdık (bkz. `TalepRepository.ara`).

**Tek implementasyonlu servis arayüzleri yok.** `TalepServisi` bir sınıf, `ITalepServisi` diye bir arayüzü yok. Kurumsal Java'da bu alışkanlık yaygındır ama tek implementasyonu olan arayüz test edilebilirlik sağlamaz (Mockito sınıfları da mock'lar) ve her gezinmede bir dosya fazladan açtırır. İkinci bir implementasyon gerçekten çıktığında arayüzü çıkarmak IDE'de tek kısayol.

---

## 3. Nesneye yönelik tasarım: nerede, neden

Aşağıdakilerin hiçbiri "OOP göstermiş olmak için" eklenmedi. Kod içinde gerçekten işe yaradıkları yerler:

### Kapsülleme (encapsulation)

| Nerede | Ne yapıyor |
|---|---|
| `Talep.durum` | `setDurum` **yok**. Durum yalnızca `durumDegistir` ile değişir; böylece geçiş kontrolü ve denetim kaydı yazımı atlanamaz |
| `Talep.getOnayKayitlari()` | `Collections.unmodifiableList` dönüyor; dışarıdan denetim izine kayıt eklenemez |
| `OnayKaydi` | Hiç setter yok. Denetim kaydı bir kez yazılır, değiştirilemez; bu kural derleme zamanında garanti |
| `Kullanici.toString()` | Şifre özetini kasıtlı olarak dışarıda bırakıyor |
| `GirisKomutu.toString()` | Record'un varsayılan `toString`'i ezildi, yoksa şifre log'a düşerdi |

Bu, kapsüllemenin ders kitabı tanımından ("alanları private yap") farklı olan asıl faydası: **geçersiz duruma girmenin yolu yok.**

### Kalıtım (inheritance)

| Nerede | Neden kalıtım |
|---|---|
| `DenetimAlanlari` (`@MappedSuperclass`) | Beş varlık aynı üç denetim alanını paylaşıyor; JPA'nın mapped superclass mekanizması bunun için var |
| `IsKuraliException` hiyerarşisi | Ortak `kod` alanı ve "hepsi `RuntimeException` türevi olmalı" kuralı tek yerde |
| `OncePerRequestFilter` genişletmesi | `JwtKimlikFiltresi` ve `KorelasyonKimligiFiltresi` çerçevenin sağladığı "istek başına bir kez" garantisini devralıyor |
| `ResponseEntityExceptionHandler` genişletmesi | Spring MVC'nin bildiği tüm hata tiplerinin doğru status koduyla dönmesi için |

Kalıtımın kullanılmadığı yer de bilinçli: `Talep`, `Birim`, `Kullanici` arasında ortak bir soyut varlık sınıfı yok, çünkü aralarında "bir çeşit" (is-a) ilişkisi yok.

### Polimorfizm

| Nerede | Nasıl |
|---|---|
| Hata yönetimi | `HataYakalayici` istisna tipine göre farklı davranıyor; yeni bir istisna tipi eklemek yalnızca bir metot eklemek demek |
| `Specification` kompozisyonu | Fonksiyonel arayüz; filtreler çalışma anında birleştiriliyor |
| `Karar` enum'u | İstemci yalnızca `ONAYLA`/`REDDET` bilir; hedef duruma çevrim `TalepServisi` içinde, durum makinesinin iç detayı istemciye sızmaz |
| `TalepDurumu` | Geçiş kuralları enum'un kendi içinde; her sabit kendi izinli hedeflerini biliyor |

### Soyutlama (abstraction) ve arayüzler

| Arayüz | Kim implemente ediyor |
|---|---|
| `UserDetailsService` | `KullaniciDetayServisi` (`loadUserByUsername` override) |
| `ApplicationRunner` | `DemoVeriYukleyici` |
| `JpaRepository`, `JpaSpecificationExecutor` | Beş repository arayüzü |
| `AuditorAware<String>` | `JpaDenetimYapilandirmasi` içinde lambda ile |
| `Specification<Talep>` | `TalepSpecifications` içindeki fabrika metotları |

### Metot ezme (override) örnekleri

`equals` / `hashCode` / `toString` (beş varlıkta), `doFilterInternal` (iki filtrede), `loadUserByUsername`, `handleMethodArgumentNotValid`, `handleExceptionInternal`, `run`.

`hashCode` ezmesinde ince bir ayrıntı var: sabit değer dönüyor (`Talep.class.hashCode()`), `getClass().hashCode()` değil. Sebep Hibernate vekilleri: tembel yüklenmiş bir `Talep` aslında üretilmiş bir alt sınıf ve `getClass()` farklı sonuç veriyor. Vekil ile gerçek nesne aynı `HashSet`'e girerse iki ayrı kayıt gibi görünürlerdi.

### Kullanılan tasarım kalıpları

Abartılı isimlendirme yapmadan, gerçekten uygulanan kalıplar:

| Kalıp | Nerede |
|---|---|
| Repository | `repository` paketi |
| Specification | `TalepSpecifications`; filtreler ayrı ayrı yazılıp birleştiriliyor |
| State (durum makinesi) | `TalepDurumu` ve geçiş tablosu |
| Observer / domain event | `TalepDurumuDegistiOlayi` + `@TransactionalEventListener` |
| Factory method | `TalepAramaKriteri.kendiTalepleri/birimTalepleri/tumTalepler`, `HataYaniti.of` |
| Template method | `OncePerRequestFilter.doFilterInternal`, `ResponseEntityExceptionHandler` |
| DTO | `service/dto` ve `service/komut` |

---

## 4. SOLID: iddia ve kanıt

| İlke | Bu projede | Kanıt |
|---|---|---|
| **S**ingle Responsibility | Her sınıfın tek değişme sebebi var: `TalepServisi` iş kuralı, `TalepDonusturucu` çevrim, `JwtUretici` token, `HataYakalayici` HTTP çevrimi | Katman testleri ve sınıf boyutları |
| **O**pen/Closed | Yeni bir talep türü eklemek: `TalepTuru` enum'una bir sabit ve migration'daki CHECK kısıtı. Servis, controller, repository değişmiyor | `TalepTuru`, `V2__talep_ve_onay_kaydi.sql` |
| **L**iskov Substitution | `IsKuraliException` türevlerinin hepsi aynı sözleşmeyi taşıyor (`getKod()` + mesaj); yakalayıcı hangisi geldiğini bilmeden davranabiliyor | `HataYakalayici.yanit(...)` |
| **I**nterface Segregation | Repository arayüzleri yalnızca ihtiyaç duyulan metotları tanımlıyor; kullanılmayan metotlar denetimde silindi | `KullaniciRepository` (4 metot), `BirimRepository` (1 metot) |
| **D**ependency Inversion | Servis somut sınıfa değil repository arayüzüne bağımlı; bağımlılıklar yapıcıdan enjekte ediliyor, alan enjeksiyonu (`@Autowired` field) hiç kullanılmıyor | Tüm servislerin yapıcıları |

Yapıcı enjeksiyonunun alan enjeksiyonuna tercih edilmesi tercih meselesi değil: yapıcı enjeksiyonu bağımlılıkları zorunlu kılar (nesne eksik bağımlılıkla oluşturulamaz), alanları `final` yapmaya izin verir ve sınıfı Spring olmadan test edilebilir bırakır. `TalepServisiTest` bu sayede Spring bağlamı ayağa kaldırmadan koşuyor.

---

## 5. Hata yönetimi stratejisi

### Katmanlara göre

| Katman | Ne yapar |
|---|---|
| `domain` | Değişmez ihlalinde alan diline ait istisna fırlatır (`GecersizDurumGecisiException`) |
| `service` | İş kuralı ihlalinde `IsKuraliException` türevi fırlatır; teknik istisnayı yakalayıp çevirmez |
| `web` | Tek merkezden HTTP karşılığına çevirir; controller'larda `try/catch` yok |
| `security` | Filtre zincirinde oluşan hatalar advice'a uğramadığı için aynı sözleşmeyi elle üretir |

### Sözleşme

Her hata yanıtı aynı dört alanı taşır: `{ kod, mesaj, detaylar, zaman }`. `detaylar` her zaman dizi (boş olabilir), böylece istemci "alan var mı" kontrolü yapmak zorunda kalmıyor.

| Durum | Kod | Ne zaman |
|---|---|---|
| 400 | `DOGRULAMA_HATASI` | Bean Validation ihlali; `detaylar` alan bazlı dolu |
| 400 | `GECERSIZ_GOVDE` | Bozuk JSON, tanımsız enum değeri |
| 400 | `GECERSIZ_PARAMETRE` | Yol veya sorgu parametresi beklenen tipte değil |
| 400 | `GECERSIZ_ISLEM` | İş kuralı ihlali (ret gerekçesiz, taslak olmayan talebi düzenleme) |
| 401 | `KIMLIK_DOGRULANAMADI` | Token yok, bozuk, süresi dolmuş; ya da şifre yanlış |
| 403 | `YETKISIZ_ISLEM` | Rol yetersiz veya kayıt bu kullanıcıya ait değil |
| 404 | `KAYIT_BULUNAMADI` / `KAYNAK_BULUNAMADI` | İstenen kayıt yok / bilinmeyen uç |
| 405 | `DESTEKLENMEYEN_METOT` | Uç bu HTTP metodunu desteklemiyor |
| 409 | `GECERSIZ_DURUM_GECISI` | Durum makinesinde tanımsız geçiş |
| 409 | `ES_ZAMANLI_DEGISIKLIK` | İyimser kilit çakışması |
| 409 | `VERI_CAKISMASI` | Veritabanı kısıtı ihlali |
| 415 | `DESTEKLENMEYEN_ICERIK_TIPI` | Content-Type desteklenmiyor |
| 500 | `SUNUCU_HATASI` | Beklenmeyen; tam stack trace sunucuda loglanır |

### İki kural

**Loglama seviyesi hatanın kime ait olduğuna göre.** Kullanıcı hatası (yanlış id, yetkisiz erişim) `WARN` veya hiç loglanmıyor; sunucu hatası `ERROR` ve tam stack trace ile. Aksi halde gerçek problem, kullanıcı hatalarının gürültüsünde kayboluyor.

**Dışarıya çıkan mesaj ile loglanan mesaj farklı.** Veritabanı kısıt adı, Jackson'ın satır/sütun bilgisi, paket içi sınıf adları log'a yazılıyor ama istemciye gitmiyor.

### Neden RFC 9457 (ProblemDetail) değil

Spring 6 standart bir hata gövdesi sunuyor (`application/problem+json`: `type`, `title`, `status`, `detail`, `instance`). Kullanmadık çünkü proje şartnamesi Türkçe alan adlı ve makine tarafından okunabilir `kod` alanı olan bir sözleşme istiyordu. `type` URI'si yerine kısa kod, bu ölçekte daha kullanışlı. Standarda geçmek istenirse tek dokunulacak yer `HataYaniti` ve `HataYakalayici`.

---

## 6. Eşzamanlılık

Tek bir eşzamanlılık senaryosu var ve ele alınmış: **iki amir aynı talebe aynı anda karar verirse ne olur.**

`Talep` varlığında `@Version` kolonu var (iyimser kilitleme). Hibernate her `UPDATE`'e `where surum = ?` ekliyor; eskimiş sürümle gelen ikinci işlem sıfır satır güncelliyor ve `OptimisticLockingFailureException` fırlatıyor. API bunu 409 `ES_ZAMANLI_DEGISIKLIK` olarak dönüyor.

Kötümser kilit (`SELECT FOR UPDATE`) yerine iyimser seçildi: çakışma nadir, kilit tutmanın maliyeti ise her istekte ödeniyor.

Kanıt: `TalepServisiTransactionTest.esZamanliGuncellemeIkincisiCakisir`.

---

## 7. Test stratejisi

```
        ▲  az sayıda, yavaş, gerçekçi
        │
   ┌────┴────┐  Güvenlik entegrasyonu (33)  gerçek filtre zinciri + gerçek PostgreSQL
   │         │  Transaction / bildirim (8)  rollback, commit sonrası olay
   ├─────────┤
   │         │  Repository (26)             gerçek PostgreSQL, N+1, tembel yükleme
   │         │  HTTP katmanı (19)           status kodları, hata sözleşmesi
   ├─────────┤
   │         │  Saf birim (60)              durum makinesi (iki kademe dahil), servis + Mockito
   │         │  Mimari (10)                 ArchUnit katman kuralları
   └─────────┘
        │
        ▼  çok sayıda, hızlı, izole
```

| Prensip | Uygulaması |
|---|---|
| Test adı ne test edildiğini söyler | `personelBaskasininTalebiniGoremez`, `denetimKaydiYazilamazsaDurumGeriSarar` |
| Testi geçirmek için test zayıflatılmaz | CI kırmızısı, testi silerek değil izolasyon düzeltilerek çözüldü |
| Her hata düzeltmesi bir regresyon testi getirir | Token yarışı, test sıra bağımlılığı, 404 yerine 500 dönmesi |
| Testler sıradan bağımsız | `-Dsurefire.runOrder=reversealphabetical` ile de yeşil |
| Ölçüm iddia yerine geçer | N+1 sayımı, sayfalama süresi ve kapsam eşiği teste bağlı |

---

## 8. Kod standartları ve araçlar

| Araç | Ne yapıyor | Nerede |
|---|---|---|
| Spotless | Kullanılmayan import temizliği, import sırası, satır sonu | `./mvnw spotless:apply`, CI'da `check` |
| JaCoCo | Kapsam eşiği: proje geneli %80, servis paketi %85 | `verify` fazında, eşik altında derleme kırılır |
| ArchUnit | Katman kuralları | `MimariKurallariTest` |
| EditorConfig | Editör farklarından doğan gürültüyü keser | `.editorconfig` |
| Dependabot | Haftalık bağımlılık ve action güncellemesi | `.github/dependabot.yml` |
| Testcontainers | Testler gerçek PostgreSQL'de | `VeritabaniTestTemeli` |

**Tam biçimlendirici (google-java-format gibi) bilinçli olarak yok.** Var olan kodu baştan biçimlendirmek, hiçbir davranış değiştirmeyen devasa bir fark yığını üretir ve `git blame`'i okunmaz hale getirir. Spotless'taki kurallar tartışma yaratmayan, otomatik düzeltilebilen türden.

### İsimlendirme

- İş alanına ait adlar Türkçe (`Talep`, `OnayKaydi`, `olusturma_tarihi`), çerçeveden gelen teknik kavramlar İngilizce (`Repository`, `Service`, `Controller`, `Dto`).
- Türkçe karakter kullanılmıyor (ı, ş, ğ yerine i, s, g): veritabanı harmanlama ve dosya sistemi sorunlarını baştan keser. Yorum satırlarında ve dokümanlarda Türkçe karakter serbest.
- Gerekçesi `docs/decisions.md` K-007 içinde.

---

## 9. On kat ölçekte ne değişirdi

Dürüst cevap: **önce ölçerdim.** Mevcut sayılara göre uygulama katmanında darboğaz yok (50 eşzamanlı istekte okuma p95 64 ms). Sıralı olarak yapılacaklar:

1. Veritabanı bağlantı havuzu ve sorgu planlarını gerçek yük altında izlemek.
2. Raporlama sorgularını okuma replikasına yönlendirmek.
3. Denetim tablosunu tarihe göre bölümlemek (partition), çünkü tek büyüyen tablo o.
4. Bildirim dinleyicisini kuyruk tüketicisine çevirmek. Bugün bunu yapmak kolay, çünkü olay zaten yayınlanıyor; servis katmanına dokunmadan değişir.

Mikroservise bölmek bu listede yok. Tek tutarlılık sınırı olan bir sistemi bölmek, ölçek problemi çözmez, dağıtık transaction problemi yaratır.
