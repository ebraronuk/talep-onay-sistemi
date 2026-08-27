# Güvenlik

Bu dosya iki soruya cevap verir: **her uç kime açık** ve **bu nasıl kanıtlandı**.

---

## 1. Kimlik doğrulama

- Yöntem: imzalı JWT, `Authorization: Bearer <token>` başlığında.
- Algoritma: HMAC-SHA. Anahtar `JWT_SECRET` ortam değişkeninden gelir; açılışta en az 32 bayt olduğu kontrol edilir, değilse uygulama başlamaz.
- Token ömrü: 60 dakika (`JWT_TTL_DAKIKA` ile değiştirilebilir).
- Sunucu tarafında oturum yok (`SessionCreationPolicy.STATELESS`).
- Şifreler BCrypt ile saklanır (güç 10, her kayıtta rastgele tuz).

**Bilinen ödün:** token sunucudan anında iptal edilemez. Bir kullanıcı pasife alınsa bile elindeki token süresi dolana kadar geçerli kalır. Anında iptal, her istekte kara liste sorgusu demek olurdu; bu da stateless olmaktan vazgeçmek anlamına gelir. 60 dakikalık ömür ile kabul edildi.

**CSRF koruması kapalı.** Bu bilinçli: CSRF saldırısı tarayıcının çerezi otomatik göndermesine dayanır. Bu API çerez kullanmıyor, token'ı JavaScript açıkça başlığa koyuyor. Çerez tabanlı bir oturuma geçilirse CSRF koruması geri açılmalıdır.

---

## 2. Uç yetki tablosu

Aşağıdaki tablo `RequestMappingHandlerMapping` üzerinden okunan gerçek uç listesiyle karşılaştırılıyor: bkz. `UcKorumaTest`.

| Metot | Uç | Kimlik | Rol kuralı | Kayıt bazlı kural |
|---|---|---|---|---|
| POST | `/api/v1/kimlik/giris` | **Gerekmez** | - | - |
| GET | `/api/v1/kimlik/ben` | Gerekir | Tüm roller | Sadece kendi bilgisi |
| POST | `/api/v1/talepler` | Gerekir | `PERSONEL` | - |
| GET | `/api/v1/talepler` | Gerekir | Tüm roller | Kapsam role göre daralır (aşağıya bakın) |
| GET | `/api/v1/talepler/{id}` | Gerekir | Tüm roller | `PERSONEL`: sahibi olmalı · `AMIR`: kendi birimi · `YONETICI`: hepsi |
| PUT | `/api/v1/talepler/{id}` | Gerekir | `PERSONEL` | Sahibi olmalı **ve** talep `TASLAK` durumunda olmalı |
| POST | `/api/v1/talepler/{id}/onaya-gonder` | Gerekir | `PERSONEL` | Sahibi olmalı |
| POST | `/api/v1/talepler/{id}/karar` | Gerekir | `AMIR`, `YONETICI` | Kademeye göre daralır (aşağıya bakın) **ve** kendi talebi olmamalı |
| GET | `/api/v1/raporlar/ozet` | Gerekir | `YONETICI` | - |
| GET | `/api/v1/bildirimler` | Gerekir | Tüm roller | Sadece kendi bildirimleri |
| GET | `/api/v1/bildirimler/okunmamis-sayisi` | Gerekir | Tüm roller | Sadece kendi |
| POST | `/api/v1/bildirimler/{id}/okundu` | Gerekir | Tüm roller | Bildirimin alıcısı olmalı |
| GET | `/actuator/health`, `/actuator/info` | **Gerekmez** | - | Konteyner sağlık kontrolü için açık |
| GET | `/actuator/**` (diğerleri) | Gerekir | `YONETICI` | - |
| GET | `/v3/api-docs/**`, `/swagger-ui/**` | **Gerekmez** | - | Geliştirme kolaylığı; üretimde kapatılabilir |

### Listeleme kapsamı role göre nasıl daralıyor

| Rol | Gördüğü küme |
|---|---|
| `PERSONEL` | Yalnızca kendi açtığı talepler |
| `AMIR` | Kendi birimindeki tüm talepler |
| `YONETICI` | Tüm birimler; `birimId` parametresiyle daraltabilir |

Kritik nokta: `PERSONEL` ve `AMIR` için kapsam **oturumdan** geliyor, istemciden gelen `birimId` parametresi yok sayılıyor. Sayılsaydı personel `?birimId=1` göndererek başkalarının taleplerini listeleyebilirdi. Bunu doğrulayan test: `personelBirimFiltresiyleKacamaz`.

### `/karar` ucu tutara göre iki kademeli

Talep tutarı yapılandırılabilir bir limiti (`talep.onay.yonetici-limiti`) aşıyorsa, `BEKLEMEDE` durumundaki talebi `AMIR` onayladığında talep doğrudan `ONAYLANDI`'ya değil `YONETICI_ONAYINDA`'ya geçer; ikinci kademede yalnızca `YONETICI` karar verebilir.

| Talebin durumu | Kimin karar verebileceği | Kimin veremeyeceği |
|---|---|---|
| `BEKLEMEDE` | `AMIR` (kendi birimi) | `YONETICI` — birinci kademeyi atlayamaz |
| `YONETICI_ONAYINDA` | `YONETICI` | `AMIR` — kendi onayladığı talebin ikinci kademesine karışamaz |

Bu kural `@PreAuthorize`'ın yakalayamayacağı bir kayıt bazlı kontrol: her iki rol de `/karar` ucuna erişebilir, ama hangi **durumdaki** talebe karar verebileceği role ve talebin o anki durumuna birlikte bakılarak belirlenir. `YONETICI_ONAYINDA -> ONAYLANDI` geçişinin kendisi durum makinesi açısından geçerli; geçersiz olan, o geçişi `AMIR`'ın yapması. Kanıt: `TalepServisiTest.IkiKademeliOnay` ve `IkiKademeliOnayAkisiTest`.

---

## 3. İki kademeli yetkilendirme

Yetki kontrolü iki ayrı yerde, iki ayrı soru soruyor:

1. **Controller'da `@PreAuthorize`:** "Bu rol bu işlemi yapabilir mi?" Örnek: onay verme yalnızca `AMIR`.
2. **Servis katmanında:** "Bu kişi bu **kayıt** üzerinde işlem yapabilir mi?" Örnek: amir kendi biriminin talebine karar verebilir, başka birimin talebine karar veremez.

Bunlar birbirinin yerine geçmez. Yalnızca birincisi olsaydı, herhangi bir amir kurumdaki her talebi onaylayabilirdi.

---

## 4. Bilgi sızdırmama kuralları

| Durum | Ne yapılıyor | Neden |
|---|---|---|
| Yanlış şifre / olmayan kullanıcı | Aynı mesaj: "Kullanici adi veya sifre hatali" | Hangi kullanıcı adlarının kayıtlı olduğu sızmasın |
| `hideUserNotFoundExceptions = true` | Kullanıcı yokken de şifre kontrolü süresi harcanır | Yanıt süresinden kullanıcı varlığı çıkarılmasın |
| Başkasının kaydına erişim | "Bu talebi goruntuleme yetkiniz yok" | "Var ama senin değil" demek, o id'de kayıt olduğunu doğrulamak olur |
| Veritabanı kısıt ihlali | Genel mesaj döner, kolon/kısıt adı loglanır | Şema detayı istemciye gitmesin |
| Beklenmeyen hata | "Beklenmeyen bir hata olustu" + sunucu tarafında tam stack trace | İç yapı sızmasın ama iz kaybolmasın |
| Loglama | Şifre ve token hiçbir log satırına yazılmaz | `GirisKomutu.toString()` şifreyi kasıtlı olarak dışarıda bırakır |

---

## 5. Bunlar nasıl kanıtlandı

| Test sınıfı | Kaç test | Neyi kanıtlıyor |
|---|---|---|
| `GuvenlikEntegrasyonTest` | 30 | Gerçek filtre zinciriyle her rolün erişebildiği ve erişemediği uçlar |
| `UcKorumaTest` | 2 | Uygulamanın kendi uç listesi okunup her biri token'sız deneniyor; kasıtlı açık uçlar dışında hepsi 401 dönmeli |
| `TalepServisiTest` (Yetki bölümü) | 8 | Kayıt bazlı kuralların birim testi |
| `TalepServisiTest` (İkiKademeliOnay bölümü) | 9 | Tutar limitine göre kademe seçimi ve kademe ihlalleri |
| `IkiKademeliOnayAkisiTest` | 4 | İki kademenin HTTP'den denetim izine uçtan uca doğrulanması |
| `TalepControllerTest` | 13 | Hata sözleşmesi ve HTTP kodları |

`UcKorumaTest` özellikle önemli: elle yazılan testler yalnızca bildiğimiz uçları korur. Yarın biri yeni bir controller metodu ekleyip güvenlik kuralını yazmayı unutursa, o testler hâlâ yeşil kalır ama `UcKorumaTest` kırmızıya döner.

### Doğrulanan saldırı senaryoları

- Token'sız istek → 401
- İmzası bozulmuş token → 401
- Süresi dolmuş token → 401
- **Başka bir anahtarla imzalanmış, rolü `YONETICI` yazan token → 401** (imza doğrulanmadan hiçbir isteme güvenilmiyor)
- `Bearer` öneki olmayan başlık → 401
- Pasife alınmış kullanıcı girişi → 401
- Personel'in `?birimId=` ile kapsam genişletmesi → sonuç değişmiyor
- Personel'in kendi talebini onaylaması → 403
- Amirin kendi talebini onaylaması → 400 (iş kuralı)
- Amirin, tutar limiti nedeniyle ikinci kademeye düşmüş bir talebe karar vermeye çalışması → 403
- Yöneticinin, henüz birim amiri onayından geçmemiş (`BEKLEMEDE`) bir talebe karar vermeye çalışması → 403

---

## 6. Üretime çıkarken yapılacaklar

Bu proje bir mülakat/portföy projesi; gerçek bir kuruma konulacaksa şunlar yapılmalı:

1. `JWT_SECRET` gizli yönetim sisteminden (Vault, AWS Secrets Manager, Kubernetes Secret) gelmeli. `application.yml` içindeki varsayılan yalnızca yerel geliştirme içindir.
2. Swagger uçları kapatılmalı: `springdoc.api-docs.enabled=false`.
3. CORS listesi gerçek alan adıyla değiştirilmeli; ters vekil arkasında aynı kaynak kullanılıyorsa liste boşaltılmalı.
4. HTTPS zorunlu hale getirilmeli (uygulama önündeki katmanda).
5. Başarısız giriş denemeleri için hız sınırlama (rate limiting) eklenmeli. Şu an yok ve bu bilinçli bir eksiklik: kapsam dışı bırakıldı.

---

## 7. Kaba kuvvet koruması

Denetimde çıkan bir açığın karşılığı: giriş ucunda hiçbir sınır yoktu, saniyede yüzlerce şifre denemesi yapmak mümkündü.

| Ayar | Değer |
|---|---|
| İzin verilen ardışık başarısız deneme | 5 |
| Kilit süresi | 15 dakika |
| Sayaç anahtarı | Kullanıcı adı (büyük/küçük harf duyarsız) |
| Sayaç sıfırlanması | Başarılı girişte |

Sayaç kontrolü şifre doğrulamasından **önce** yapılıyor: limiti aşmış bir kullanıcı için BCrypt hiç çalıştırılmıyor. Bu önemli, çünkü BCrypt kasıtlı olarak pahalı (her deneme yaklaşık 100 ms bir CPU çekirdeği). Sınır olmadan bu, ucuz bir hizmet dışı bırakma (DoS) yolu oluyordu.

**Neden IP değil kullanıcı adı bazlı:** kurumsal ağlarda yüzlerce kullanıcı tek bir NAT adresinin arkasından çıkar; IP bazlı sayım tüm kurumu birlikte kilitler. Karşılığı, saldırganın farklı kullanıcı adlarıyla denemeye devam edebilmesi; onu yavaşlatan şey BCrypt'in maliyeti.

**Bilinen sınır:** sayaç bu uygulama örneğine ait, bellekte tutuluyor. Uygulama birden fazla kopya halinde çalıştırılırsa saldırgan kopyalar arasında gezinerek limiti kopya sayısıyla çarpar. Doğru çözüm yük dengeleyici veya API gateway seviyesinde hız sınırlama, ya da paylaşımlı bir sayaç (Redis). Tek konteynerli bu dağıtım için mevcut çözüm yeterli ve "hiç yok"tan çok daha iyi.

Testler: `GuvenlikEntegrasyonTest.KabaKuvvet` (3 test).

---

## 8. Denetim izi veritabanı seviyesinde kilitli

Bir başka denetim bulgusu: "onay kaydı silinemez ve değiştirilemez" iddiası yalnızca Java tarafında doğruydu (`OnayKaydi` sınıfında setter yok). Veritabanına bağlanan herhangi biri satırı güncelleyip silebiliyordu.

`V5__denetim_izi_degistirilemez.sql` ile `onay_kaydi` tablosuna `UPDATE` ve `DELETE`'i reddeden trigger eklendi. Ekleme serbest, değişiklik yasak.

**Neden trigger, kolon seviyesinde `GRANT` değil:** `GRANT` rol bazlı çalışır ve uygulama rolünün değişmesiyle sessizce devre dışı kalabilir. Trigger, hangi rolle bağlanıldığından bağımsız çalışır.

Testler: `DenetimIziKorumasiTest` (3 test). Testler JPA'yı baypas edip doğrudan SQL çalıştırıyor; korumanın uygulamada değil veritabanında olduğunu göstermenin tek yolu bu.

---

## 9. Üretim profili

`SPRING_PROFILES_ACTIVE=prod` ile açıldığında:

| Ayar | Değişiklik | Neden |
|---|---|---|
| Swagger ve `/v3/api-docs` | Kapalı | Uç listesi ve şema dışarıya verilmemeli |
| Actuator uçları | Yalnızca `health` ve `metrics` | `env` ve `configprops` gizli değer sızdırabilir |
| Sağlık detayı | Gizli | Bileşen adları ve bağlantı bilgisi sızmasın |
| Flyway `clean` | Devre dışı | Şema temizleme komutu kazara çalıştırılamasın |
| Log seviyesi | `root: WARN` | Gürültü azalır, gerçek hata görünür |

Ek olarak `UretimYapilandirmaDenetimi` açılışta çalışıyor: depodaki varsayılan JWT anahtarıyla üretim profilinde başlatılmaya çalışılırsa uygulama **hata verip durur**. Uyarı değil hata, çünkü uyarı log'da kaybolur; yanlış yapılandırmayla ayakta duran bir sistem, ayakta olmayan sistemden kötüdür.
