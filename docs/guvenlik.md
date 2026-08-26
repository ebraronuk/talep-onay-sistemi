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
| POST | `/api/kimlik/giris` | **Gerekmez** | - | - |
| GET | `/api/kimlik/ben` | Gerekir | Tüm roller | Sadece kendi bilgisi |
| POST | `/api/talepler` | Gerekir | `PERSONEL` | - |
| GET | `/api/talepler` | Gerekir | Tüm roller | Kapsam role göre daralır (aşağıya bakın) |
| GET | `/api/talepler/{id}` | Gerekir | Tüm roller | `PERSONEL`: sahibi olmalı · `AMIR`: kendi birimi · `YONETICI`: hepsi |
| PUT | `/api/talepler/{id}` | Gerekir | `PERSONEL` | Sahibi olmalı **ve** talep `TASLAK` durumunda olmalı |
| POST | `/api/talepler/{id}/onaya-gonder` | Gerekir | `PERSONEL` | Sahibi olmalı |
| POST | `/api/talepler/{id}/karar` | Gerekir | `AMIR` | Aynı birim olmalı **ve** kendi talebi olmamalı |
| GET | `/api/raporlar/ozet` | Gerekir | `YONETICI` | - |
| GET | `/api/bildirimler` | Gerekir | Tüm roller | Sadece kendi bildirimleri |
| GET | `/api/bildirimler/okunmamis-sayisi` | Gerekir | Tüm roller | Sadece kendi |
| POST | `/api/bildirimler/{id}/okundu` | Gerekir | Tüm roller | Bildirimin alıcısı olmalı |
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

---

## 6. Üretime çıkarken yapılacaklar

Bu proje bir mülakat/portföy projesi; gerçek bir kuruma konulacaksa şunlar yapılmalı:

1. `JWT_SECRET` gizli yönetim sisteminden (Vault, AWS Secrets Manager, Kubernetes Secret) gelmeli. `application.yml` içindeki varsayılan yalnızca yerel geliştirme içindir.
2. Swagger uçları kapatılmalı: `springdoc.api-docs.enabled=false`.
3. CORS listesi gerçek alan adıyla değiştirilmeli; ters vekil arkasında aynı kaynak kullanılıyorsa liste boşaltılmalı.
4. HTTPS zorunlu hale getirilmeli (uygulama önündeki katmanda).
5. Başarısız giriş denemeleri için hız sınırlama (rate limiting) eklenmeli. Şu an yok ve bu bilinçli bir eksiklik: kapsam dışı bırakıldı.
