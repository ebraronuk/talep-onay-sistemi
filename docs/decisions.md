# Mimari Karar Günlüğü

Bu dosya projedeki her önemli teknik kararı üç satırda kayda geçirir: **karar**, **gerekçe**, **reddedilen alternatif**. Amaç altı ay sonra "bu neden böyle yapılmış" sorusuna bakmadan cevap verebilmek.

---

## Kapsam

### Sistem ne yapacak

Kurumsal bir talep ve onay iş akışı yönetir:

1. Personel bir talep oluşturur (taslak olarak kaydedebilir, sonra onaya gönderir).
2. Talep, personelin bağlı olduğu birimin amirine düşer.
3. Amir talebi onaylar veya reddeder; her iki durumda da gerekçe yazabilir.
4. Her durum değişikliği kalıcı bir onay kaydına (denetim izi) yazılır ve silinemez.
5. Yönetici rolü, birim bazında talepleri listeler ve özet raporu görür. Talep tutarı yapılandırılabilir bir limiti aşıyorsa, birim amirinin onayından sonra ikinci kademe onayı da yönetici verir.
6. Kimlik doğrulama JWT ile yapılır, yetkilendirme rol bazlıdır.

### Sistem ne YAPMAYACAK

Aşağıdakiler bilinçli olarak kapsam dışıdır. "İleride lazım olur" gerekçesiyle hiçbiri eklenmeyecek:

- Çok kiracılı (multi-tenant) yapı. Tek kurum varsayımı geçerli.
- Dinamik / kullanıcı tanımlı onay akışı motoru. Kademe sayısı sabit (en fazla iki) ve kademeye düşme kuralı (tutar limiti) koddan/yapılandırmadan gelir; kullanıcı yeni bir onay adımı tanımlayamaz.
- Dosya eki yükleme ve saklama.
- E-posta veya SMS gönderimi. Bildirim yalnızca uygulama içi kayıttır.
- Soft delete ve kayıt versiyonlama. Denetim izi zaten `onay_kaydi` tablosunda tutulur.
- Şifre sıfırlama, e-posta doğrulama, kendi kendine kayıt olma akışları. Kullanıcılar sistemde tanımlıdır.
- Mikroservisler, servis keşfi, API gateway, dağıtık izleme.
- Raporun PDF veya Excel olarak dışa aktarımı.

### Roller

| Rol | Yetki |
|---|---|
| `PERSONEL` | Kendi talebini oluşturur, görür, onaya gönderir. Başkasının talebini göremez. |
| `AMIR` | Kendi biriminin bekleyen taleplerini görür, onaylar veya reddeder. |
| `YONETICI` | Tüm birimlerin taleplerini görür, özet raporu alır. Tutar limitini aşan taleplerde birim amirinden sonra ikinci kademe onayı verir; birinci kademeye karışamaz. |

---

## K-001: Monolit, mikroservis değil

**Karar.** Uygulama tek bir Spring Boot dağıtım birimi olarak yazılır. Katman ayrımı paket düzeyinde yapılır: `domain`, `repository`, `service`, `web`, `config`, `security`.

**Gerekçe.** Sistemin tek bir veri tutarlılığı sınırı var: talep durumu ile onay kaydı aynı transaction içinde değişmek zorunda. Bunu tek veritabanı ve tek `@Transactional` sınırı ile ücretsiz elde ediyoruz. Beklenen kullanıcı sayısı ve ekip sayısı bir; mikroservisin çözdüğü problem (bağımsız ölçekleme ve bağımsız dağıtım) burada mevcut değil.

**Reddedilen alternatif.** Talep servisi + bildirim servisi + kimlik servisi şeklinde üçe bölme. Reddedildi çünkü tek transaction'da yapılan işi dağıtık transaction veya saga ile yapmak zorunda kalırdık; bu, hiç var olmayan bir ölçek problemi için gerçek bir tutarlılık problemi satın almak olurdu.

---

## K-002: PostgreSQL, tek ilişkisel veritabanı

**Karar.** Kalıcılık için PostgreSQL 16 kullanılır. Tek şema, tek veritabanı.

**Gerekçe.** Veri modeli baştan sona ilişkisel: kullanıcı bir birime bağlı, talep bir kullanıcıya bağlı, onay kaydı bir talebe bağlı. Sorguların çoğu bu ilişkiler üzerinden filtreleme ve sayfalama. Ayrıca "talep durumu değişti ve onay kaydı yazıldı" işlemi atomik olmak zorunda; ACID transaction pazarlık konusu değil.

**Reddedilen alternatif.** MongoDB. Reddedildi çünkü onay kaydını talebin içine gömmek denetim izini talebin yaşam döngüsüne bağımlı hale getirir, gömmemek ise ilişkisel veritabanında bedavaya gelen join'i uygulama katmanında elle yazmak demektir. Şemasızlığın avantaj olduğu bir durum yok: şema zaten belli ve sabit.

---

## K-003: JWT, sunucu tarafı session değil

**Karar.** Kimlik doğrulama, `Authorization: Bearer <token>` başlığı ile taşınan imzalı JWT üzerinden yapılır. Sunucu tarafında oturum durumu tutulmaz (`SessionCreationPolicy.STATELESS`).

**Gerekçe.** İstemci ayrı bir kaynakta çalışan React uygulaması. Stateless token, sunucunun yatay ölçeklenmesini sticky session veya paylaşımlı session deposu olmadan mümkün kılıyor. Rol bilgisi token içinde taşındığı için her istekte kullanıcı tablosuna gitmeye gerek kalmıyor.

**Reddedilen alternatif.** `JSESSIONID` çerezi ile klasik session. Reddedildi çünkü ayrı kaynaktaki istemci için CSRF ve çerez ayarlarını (SameSite, secure, domain) yönetmek gerekirdi ve birden fazla uygulama kopyası çalıştırıldığında session'ı paylaşmak için Redis gibi ek bir bileşen zorunlu olurdu.

**Bilinen ödün.** JWT sunucudan anında iptal edilemez. Bu proje için kabul edilebilir; token ömrü 60 dakika ile sınırlandırıldı. Gerçek bir iptal ihtiyacı doğarsa çözüm kara liste tablosudur, bu proje kapsamında yok.

---

## K-004: Spring Boot 3.5 (LTS hattı), 4.x değil

**Karar.** Spring Boot 3.5.16 ve Java 21 kullanılır.

**Gerekçe.** Proje, Türkiye'deki kamu ve finans kurumlarının ilanlarındaki gereklilikleri karşılamak için yazılıyor. Bu kurumların üretim ortamlarında yaygın olan hat Spring Boot 3.x ve Java 17/21. Kütüphane ekosisteminin (springdoc, Testcontainers, Flyway) bu hat üzerindeki uyumu da olgun.

**Reddedilen alternatif.** Spring Boot 4.1 (bu tarihte varsayılan sürüm). Reddedildi çünkü hedef kurumların çalıştırdığı sürüm değil ve mülakatta "neden 4 kullandın" sorusu, "neden 3 kullandın" sorusundan daha zor savunulur. Sürümü yükseltmek ileride tek satırlık bir değişiklik.

---

## K-005: Şema Flyway ile elle yazılır, Hibernate üretmez

**Karar.** Veritabanı şeması `src/main/resources/db/migration` altında elle yazılmış, versiyonlu SQL dosyalarıyla yönetilir. Hibernate ayarı `ddl-auto: validate`.

**Gerekçe.** Şemanın kaynağı tek olmalı ve o kaynak sürüm kontrolünde durmalı. Elle yazılan migration, indeks ve kısıt (constraint) üzerinde tam kontrol veriyor; Hibernate'in ürettiği şema bunları ya atlıyor ya da isimlendirmeyi rastgele yapıyor. `validate` ayarı, entity ile şema birbirinden ayrıldığında uygulamayı açılışta patlatır, bu istenen davranıştır.

**Reddedilen alternatif.** `ddl-auto: update`. Reddedildi çünkü üretimde asla kullanılamayacak bir ayarı geliştirmede kullanmak, geliştirme ile üretim arasında ilk günden ayrım yaratır. Ayrıca `update` kolon silmez ve indeks eklemez, yani sessizce yanlış şema üretir.

---

## K-006: Testler gerçek PostgreSQL üzerinde (Testcontainers), H2 değil

**Karar.** Repository ve entegrasyon testleri, Testcontainers ile ayağa kaldırılan gerçek bir PostgreSQL konteynerinde çalışır.

**Gerekçe.** H2'nin PostgreSQL uyumluluk modu tam değil: `ON CONFLICT`, kısmi indeks, `citext`, tarih/saat davranışı ve kilitleme semantiği farklı. H2'de yeşil olup üretimde patlayan test, hiç olmayan testten daha zararlı çünkü yanlış güven veriyor.

**Reddedilen alternatif.** H2 in-memory. Reddedildi. Ödünü kabul ediyoruz: test süresi konteyner başlatma nedeniyle uzuyor. Bunu, konteyneri tüm test sınıfları arasında paylaşarak (tek statik konteyner) dengeliyoruz.

---

## K-007: Alan adları Türkçe, teknik terimler İngilizce

**Karar.** İş alanına ait sınıf, tablo ve kolon adları Türkçe (`Talep`, `OnayKaydi`, `olusturma_tarihi`); çerçeveden gelen teknik kavramlar İngilizce (`Repository`, `Service`, `Controller`, `Dto`). Türkçe karakter kullanılmaz (ı, ş, ğ yerine i, s, g).

**Gerekçe.** İş dili Türkçe; alan adlarını çevirmek ("Request", "ApprovalRecord") kod ile şartname arasında sürekli bir sözlük taşıma yükü yaratır. Türkçe karakter kullanılmaması, veritabanı harmanlama (collation) ve dosya sistemi kaynaklı sorunları baştan keser.

**Reddedilen alternatif.** Her şeyin İngilizce olması. Reddedildi çünkü bu projenin muhatabı Türkçe konuşan bir ekip ve tutarlı bir karma, tutarsız bir tek dilden iyidir.

---

## K-008: Durum makinesi enum içinde, iş akışı motoru yok

**Karar.** Talep durumları `TalepDurumu` enum'u ile modellenir. İzin verilen geçişler enum içinde tanımlanır; geçersiz geçiş `GecersizDurumGecisiException` fırlatır.

**Gerekçe.** Toplam dört durum ve dört geçiş var. Bu, tek bir enum ve bir `Set` ile eksiksiz ifade edilebilir ve tek bakışta okunabilir.

**Reddedilen alternatif.** Spring State Machine veya Camunda. Reddedildi çünkü dört geçişlik bir kural için XML/DSL yapılandırması, ayrı bir çalışma zamanı ve yeni bir kavram seti getirir. "Bunu silsek ne kaybederiz" sorusunun cevabı: hiçbir şey.

---

## K-009: Geri alma stratejisi ileri yönlü migration, undo değil

**Karar.** Yanlış giden bir şema değişikliği, o değişikliği geri alan **yeni** bir migration ile düzeltilir. Uygulanmış bir migration dosyası asla düzenlenmez.

**Gerekçe.** Flyway Community sürümünde `undo` komutu yok; bu ticari sürüme ait bir özellik. Bundan bağımsız olarak, üretimde uygulanmış bir migration'ı geri sarmak veri kaybı riski taşır: kolon silen bir undo, o kolona yazılmış veriyi yok eder. İleri yönlü düzeltme, şema geçmişini doğrusal ve denetlenebilir tutar.

**Reddedilen alternatif.** Elle yazılmış `U<versiyon>__...sql` geri alma dosyaları. Reddedildi çünkü test edilmeyen geri alma betiği, olmayan geri alma betiğinden daha tehlikelidir; kriz anında ilk kez çalıştırılır ve genellikle patlar.

**Doğrulanan davranış.** `./mvnw flyway:clean` ile şema tamamen boşaltılıp `./mvnw flyway:migrate` yeniden çalıştırıldığında üç migration sıfırdan hatasız uygulanıyor. Bu, migration'ların idempotent bir başlangıç noktasından tekrar üretilebilir olduğunu gösteriyor.

---

## K-010: Demo verisi Flyway migration'ı değil, profile bağlı bir bileşen

**Karar.** Örnek kullanıcı ve talep verisi `demo` profili altında çalışan bir Spring bileşeni tarafından yüklenir; `db/migration` altında yer almaz.

**Gerekçe.** Migration klasörü şemanın tanımıdır ve üretimde de çalışır. Demo verisini oraya koymak, üretim veritabanına test kullanıcısı yazmak anlamına gelir. Ayrıca Flyway sürüm numaraları tüm konumlar arasında benzersiz olmak zorunda olduğundan, konum bazlı ayırma ilerideki sürüm çakışmalarına açık bir tuzak.

**Reddedilen alternatif.** `V900__demo_verisi.sql`. Reddedildi.

---

## K-011: Bildirim ana işlemden ayrı transaction'da yazılır

**Karar.** Talep durumu değiştiğinde bir uygulama olayı yayınlanır; bildirim kaydını `@TransactionalEventListener(phase = AFTER_COMMIT)` ile çalışan ve `REQUIRES_NEW` ile kendi transaction'ını açan bir dinleyici yazar.

**Gerekçe.** Onay işlemi ile bildirim yazımı farklı önem seviyesinde. Onay iş açısından asıl olan; bildirim yan etki. Bildirim tablosuna yazım herhangi bir sebeple başarısız olursa onayın geri sarmasını istemiyoruz: amir onayı verdi, sistem "olmadı" derse iş durur.

**Reddedilen alternatif.** Bildirimi aynı transaction içinde servis metodundan doğrudan yazmak. Reddedildi çünkü bildirimdeki bir hata, hiç ilgisi olmayan bir onay işlemini geri sarardı.

**Dikkat edilen nokta.** Hata yutulmuyor, `ERROR` seviyesinde loglanıyor. Sessizce yutulsaydı kimsenin haberi olmadan bildirimler kaybolurdu. Ayrıca olay nesnesi varlık değil **id** taşıyor: dinleyici commit'ten sonra çalıştığı için varlıklar o noktada kalıcılık bağlamından ayrılmış oluyor.

**Kanıt.** `TalepServisiTransactionTest.rollbackOlanIslemdeBildirimYok` ve `commitSonrasiBildirimYazilir`.

---

## K-012: Ön yüzde token `sessionStorage`'da tutulur

**Karar.** JWT tarayıcıda `sessionStorage` içinde saklanır.

**Gerekçe.** Arka uç bilinçli olarak stateless ve çerezsiz (K-003). Token'ı istemcinin bir yerde tutması gerekiyor. `sessionStorage`, `localStorage`'a göre daha dar: sekme kapanınca silinir.

**Bilinen zayıflık.** XSS. Sayfaya kod enjekte edebilen biri token'ı okuyabilir. `HttpOnly` çerez bu saldırıya kapalıdır ama beraberinde CSRF korumasını ve aynı site ayarlarını getirir; bu da stateless tasarımdan vazgeçmek anlamına gelir.

**Reddedilen alternatif.** `localStorage`. Reddedildi: kalıcı olması ek fayda sağlamıyor, saldırı yüzeyini genişletiyor.

---

## K-013: Testler çalışma sırasından bağımsız olmalı

**Karar.** Hiçbir test başka bir testin bıraktığı veriye güvenmez ve kendi verisini geride bırakmaz. Surefire çalışma sırası `alphabetical` olarak sabitlendi.

**Gerekçe.** Bu kural kâğıt üzerinde değil, gerçek bir arızadan sonra yazıldı. Yerelde 120 test yeşilken CI kırmızı yandı: Surefire'ın varsayılan `filesystem` sırası iki ortamda farklı çıkıyor ve `@SpringBootTest` sınıflarının commit ettiği veri, sonra çalışan `@DataJpaTest` sınıflarının kurulumunu tekillik kısıtından patlatıyordu.

İki önlem alındı:

1. `@Transactional` olmayan entegrasyon testleri hem `@BeforeEach` hem `@AfterEach` içinde temizlik yapar.
2. Her test sınıfı kendi benzersiz birim kodlarını kullanır (`RPO-BT`, `GVN-BT`, `TRX`, `BLD`).

Sıranın sabitlenmesi asıl çözüm değil, hatayı tekrar üretilebilir yapan destek önlemi. Doğrulama: `./mvnw test -Dsurefire.runOrder=reversealphabetical` de yeşil.

---

## K-014: Denetim izi veritabanı trigger'ı ile korunur

**Karar.** `onay_kaydi` tablosuna `UPDATE` ve `DELETE`'i reddeden bir PostgreSQL trigger'ı eklendi.

**Gerekçe.** Denetim izinin tüm değeri değiştirilemez olmasından geliyor. Koruma yalnızca `OnayKaydi` sınıfında setter olmamasıyla sağlanıyordu; yani iddia kod için doğru, sistem için yanlıştı. Veritabanına bağlanan başka bir uygulama, elle açılan bir `psql` oturumu ya da ileride yazılacak bir toplu iş geçmişi değiştirebilirdi.

**Reddedilen alternatif.** Uygulama rolünden `UPDATE`/`DELETE` yetkisini `REVOKE` etmek. Reddedildi çünkü rol bazlı yetki, uygulama rolü değiştiğinde sessizce devre dışı kalır ve bu değişiklik kimsenin dikkatini çekmez. Trigger, hangi rolle bağlanıldığından bağımsız çalışır.

**Yan etkisi.** Testlerin temizlik için attığı `DELETE` de artık trigger'a takılıyor. Test temizliği `TRUNCATE`'e çevrildi; `TRUNCATE` satır seviyesindeki trigger'ları tetiklemiyor ve zaten daha hızlı.

---

## K-015: İyimser kilitleme (`@Version`), kötümser kilit değil

**Karar.** `Talep` varlığına `@Version` kolonu eklendi.

**Gerekçe.** İki amirin aynı talebe aynı anda karar vermesi mümkün: ikisi de `BEKLEMEDE` görüyor, biri onaylıyor, diğeri reddediyor. Bu kolon olmadan ikinci yazım birincinin üzerine sessizce geçiyor ve denetim izinde iki çelişkili kayıt kalıyordu.

**Reddedilen alternatif.** Kötümser kilit (`SELECT ... FOR UPDATE`). Reddedildi çünkü çakışma nadir bir olay, kilit tutmanın maliyeti ise her istekte ödeniyor. Ayrıca kilit tutan bir istemcinin ölmesi diğerlerini bekletir.

**Kullanıcıya yansıması.** İkinci işlem 409 `ES_ZAMANLI_DEGISIKLIK` alıyor ve "bu kayıt siz görüntülerken değiştirildi, sayfayı yenileyin" mesajını görüyor. Sessizce üzerine yazmaktan iyi.

---

## K-016: URL tabanlı API sürümleme (`/api/v1`)

**Karar.** Tüm uçlar `/api/v1` öneki altında.

**Gerekçe.** Kırıcı bir değişiklik gerektiğinde eski istemcileri çalışır bırakıp yeni sürümü yanına koyabilmek için. Öneki sonradan eklemek, tüm istemcileri aynı anda güncellemeyi gerektirir ki bu genelde mümkün değildir.

**Reddedilen alternatif.** Başlık tabanlı sürümleme (`Accept: application/vnd.talep.v1+json`). Teknik olarak daha zarif ama tarayıcıdan denemesi zor, önbellek katmanlarıyla sorunlu ve ekipteki herkesin bilmesi gereken bir kural getiriyor. URL'de görünen sürüm herkes için okunabilir.

---

## K-017: Giriş denemesi sınırlaması uygulama içinde, bellekte

**Karar.** Ardışık 5 başarısız denemeden sonra kullanıcı 15 dakika kilitleniyor. Sayaç Caffeine önbelleğinde, uygulama belleğinde.

**Gerekçe.** Sınır olmadan giriş ucu hem kaba kuvvet hem de ucuz bir hizmet dışı bırakma yoluydu (BCrypt her denemede bir çekirdeği ~100 ms meşgul ediyor). Tek konteynerli bu dağıtım için bellekteki sayaç yeterli.

**Reddedilen alternatif.** Redis'te paylaşımlı sayaç. Reddedildi çünkü tek bir özellik için yeni bir çalışma zamanı bileşeni getiriyor.

**Bilinen sınır.** Uygulama birden fazla kopya halinde çalıştırılırsa limit kopya sayısıyla çarpılır. Doğru yer, o senaryoda, yük dengeleyici veya API gateway. Bu sınır `docs/guvenlik.md` içinde açıkça yazılı.

---

## K-018: İş metrikleri Micrometer ile

**Karar.** `talep.olusturuldu`, `talep.durum.degisti` sayaçları ve `talep.karar.suresi` zamanlayıcısı eklendi.

**Gerekçe.** Actuator kutudan JVM ve HTTP metrikleri veriyor; bunlar "sistem ayakta mı" sorusuna cevap veriyor ama "sistem işe yarıyor mu" sorusuna vermiyor. "Dün kaç talep onaylandı, onay ortalama ne kadar sürdü" soruları operasyonun asıl sorduğu sorular.

**Dikkat edilen nokta.** Etiketler yalnızca enum değerleriyle sınırlı. Etiket olarak kullanıcı adı veya talep başlığı konsaydı her yeni değer yeni bir zaman serisi üretirdi; metrik veritabanını şişiren klasik hata bu.

---

## K-019: Mutasyon testi ayrı profilde, kapı olarak değil ölçüm olarak

**Karar.** PIT (pitest) `mutasyon` profiliyle çalışıyor, normal derlemeye dahil değil ve bir eşiği yok.

**Gerekçe.** Mutasyon testi test paketini onlarca kez çalıştırıyor; her derlemede koşturmak dakikalar ekler. Eşik koymamanın sebebi ise skorun anlamlı kısmının sınıf bazında olması: basit getter'lardaki hayatta kalan mutasyonlar için test yazmak skoru süsler, hiçbir şey kanıtlamaz.

**Ne kazandırdı.** İlk çalıştırmada gerçek bir test boşluğu buldu: `TalepServisi.guncelle` metodundan açıklama güncellemesini silmek hiçbir testi kırmıyordu. Detay `docs/performans.md` bölüm 6.

---

## K-020: Onay yetkisi tutara göre iki kademeli, sabit ve yapılandırma bazlı

**Karar.** Talebe bir `tutar` alanı eklendi. Bu alan doluysa ve yapılandırılabilir bir limiti (`talep.onay.yonetici-limiti`) aşıyorsa, birim amirinin onayından sonra talep `ONAYLANDI` yerine yeni bir ara duruma (`YONETICI_ONAYINDA`) geçer ve ikinci kademede yalnızca `YONETICI` rolü karar verebilir. Limit koda gömülmedi; `OnayAyarlari` adlı bir `@ConfigurationProperties` record'u üzerinden ortam değişkeniyle (`ONAY_YONETICI_LIMITI`) verilir.

**Gerekçe.** Gerçek kurumsal onay akışlarında yetki çoğunlukla tutara bağlıdır: birim amiri belli bir limite kadar tek başına onaylayabilir, üstünü daha üst bir makam onaylar. Bunu koda sabit bir sayı olarak gömmek yerine yapılandırmaya taşımanın sebebi, kurumun limiti değiştiğinde kodun değişmemesi, yeniden derlenmemesi ve yeniden test edilmemesi gerekliliği: bu bir iş kuralı parametresi, teknik bir sabit değil.

**Reddedilen alternatif 1.** Genel amaçlı, kullanıcı tanımlı onay akışı motoru (kaç kademe olacağını, hangi rolün onaylayacağını çalışma zamanında yapılandırma). Reddedildi çünkü kapsam sabit iki kademe; dinamik bir motorun getirdiği karmaşıklığın karşılığı yok (bkz. K-008'in aynı gerekçesi, durum makinesi için).

**Reddedilen alternatif 2.** Onay zincirini rol hiyerarşisiyle ifade etmek (`AMIR` her zaman ilk, `YONETICI` her zaman ikinci onaylayıcı, tutardan bağımsız). Reddedildi çünkü bu, tutarı düşük taleplerde de yöneticiyi işin içine sokar; personelin izin talebi gibi parasal karşılığı olmayan taleplerde gereksiz bir kademe eklenmiş olurdu. Kademeye düşüp düşmeme kararının kendisi de tutara bağlı olmalı.

**Doğrulanan davranış.** Kademe geçişinin kayıt bazlı yetki kısmı (`kararVerebilirMi`) durum makinesinin yakalayamayacağı bir ihlali önlüyor: `YONETICI_ONAYINDA -> ONAYLANDI` geçişi durum makinesi açısından geçerli, ama bu geçişi bir `AMIR`'ın yapması geçersiz. Kanıt: `TalepServisiTest.IkiKademeliOnay.amirIkinciKademeyeKarisamaz`, `IkiKademeliOnayAkisiTest.ikinciKademeyeAmirKarisamaz`.
