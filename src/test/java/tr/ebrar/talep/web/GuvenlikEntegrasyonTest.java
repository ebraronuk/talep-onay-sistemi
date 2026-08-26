package tr.ebrar.talep.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.security.GirisDenemeTakipcisi;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Gercek guvenlik zinciriyle uctan uca yetki testleri.
 *
 * <p>Buradaki testler mulakatta "yetkilendirmeyi nasil test ettin" sorusunun cevabi.
 * Her rol icin erisemeyecegi uclar tek tek deneniyor; yesil olmalari, yetki
 * kurallarinin yalnizca kagitta degil calisan kodda oldugunu gosteriyor.
 *
 * <p>Uclarin tam listesi ve beklenen yetki kurallari: docs/guvenlik.md
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuvenlikEntegrasyonTest extends VeritabaniTestTemeli {

    private static final String SIFRE = "Parola123!";

    private static final List<String> TUM_TEST_KULLANICILARI =
            List.of("g.personel", "g.diger", "g.amir", "g.muhamir", "g.yonetici", "g.muhpersonel");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private OnayKaydiRepository onayKaydiRepository;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    @Autowired
    private GirisDenemeTakipcisi denemeTakipcisi;

    @Autowired
    private PasswordEncoder sifreKodlayici;

    @Value("${talep.jwt.gizli-anahtar}")
    private String gizliAnahtar;

    private String personelToken;
    private String digerPersonelToken;
    private String amirToken;
    private String baskaBirimAmirToken;
    private String yoneticiToken;

    private Long personelTalebiId;
    private Long digerPersonelTalebiId;
    private Long baskaBirimTalebiId;

    @BeforeEach
    void hazirla() throws Exception {
        temizle();
        // Deneme sayaci tekil bir bean ve testler arasinda paylasiliyor. Onceki bir
        // test kullaniciyi kilitlemis olabilir; kurulumda sayaclar sifirlaniyor.
        TUM_TEST_KULLANICILARI.forEach(denemeTakipcisi::basariliGiris);

        Birim btgm = birimRepository.save(new Birim("GVN-BT", "Guvenlik testi BT birimi"));
        Birim muhasebe = birimRepository.save(new Birim("GVN-MH", "Guvenlik testi muhasebe birimi"));

        Kullanici personel = kaydet("g.personel", Rol.PERSONEL, btgm);
        Kullanici digerPersonel = kaydet("g.diger", Rol.PERSONEL, btgm);
        kaydet("g.amir", Rol.AMIR, btgm);
        kaydet("g.muhamir", Rol.AMIR, muhasebe);
        kaydet("g.yonetici", Rol.YONETICI, btgm);
        Kullanici muhPersonel = kaydet("g.muhpersonel", Rol.PERSONEL, muhasebe);

        personelTalebiId = talepKaydet("Personel talebi", personel, TalepDurumu.BEKLEMEDE);
        digerPersonelTalebiId = talepKaydet("Diger personel talebi", digerPersonel, TalepDurumu.TASLAK);
        baskaBirimTalebiId = talepKaydet("Muhasebe talebi", muhPersonel, TalepDurumu.BEKLEMEDE);

        personelToken = girisYap("g.personel");
        digerPersonelToken = girisYap("g.diger");
        amirToken = girisYap("g.amir");
        baskaBirimAmirToken = girisYap("g.muhamir");
        yoneticiToken = girisYap("g.yonetici");
    }

    /**
     * Bu sinif @Transactional DEGIL: gercek filtre zinciriyle calismasi icin
     * istekler kendi transaction'larini aciyor ve veri commit oluyor. Dolayisiyla
     * temizligi elle yapmak zorundayiz. Hem oncesinde hem sonrasinda temizliyoruz:
     * sadece @BeforeEach yeterli degildi, son test kendi verisini geride birakip
     * baska bir sinifin kurulumunu tekil kisittan patlatiyordu (CI'da yakalandi).
     */
    @AfterEach
    void temizle() {
        veritabaniTemizleyici.hepsiniTemizle();
    }

    private Kullanici kaydet(String kullaniciAdi, Rol rol, Birim birim) {
        return kullaniciRepository.save(new Kullanici(
                kullaniciAdi, kullaniciAdi + " Test", kullaniciAdi + "@ornek.gov.tr",
                sifreKodlayici.encode(SIFRE), rol, birim));
    }

    private Long talepKaydet(String baslik, Kullanici sahip, TalepDurumu durum) {
        Talep talep = new Talep(baslik, baslik + " aciklamasi", TalepTuru.DIGER, sahip);
        if (durum == TalepDurumu.BEKLEMEDE) {
            talep.durumDegistir(TalepDurumu.BEKLEMEDE, sahip, null);
        }
        return talepRepository.save(talep).getId();
    }

    private String girisYap(String kullaniciAdi) throws Exception {
        String govde = mockMvc.perform(post("/api/v1/kimlik/giris")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kullaniciAdi": "%s", "sifre": "%s"}
                                """.formatted(kullaniciAdi, SIFRE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return govde.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // --- kimlik dogrulama -----------------------------------------------

    @Nested
    @DisplayName("Kimlik dogrulama")
    class Kimlik {

        @Test
        @DisplayName("Tokensiz istek 401 ve sozlesmeye uygun hata govdesi doner")
        void tokensiz401() throws Exception {
            mockMvc.perform(get("/api/v1/talepler"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.kod").value("KIMLIK_DOGRULANAMADI"))
                    .andExpect(jsonPath("$.zaman").exists());
        }

        @Test
        @DisplayName("Imzasi bozuk token 401")
        void bozukToken401() throws Exception {
            mockMvc.perform(get("/api/v1/talepler").header("Authorization", bearer(personelToken + "bozuk")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Bearer oneksiz baslik 401")
        void oneksizBaslik401() throws Exception {
            mockMvc.perform(get("/api/v1/talepler").header("Authorization", personelToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Suresi dolmus token 401")
        void suresiDolmusToken401() throws Exception {
            SecretKey anahtar = Keys.hmacShaKeyFor(gizliAnahtar.getBytes(StandardCharsets.UTF_8));
            Instant gecmis = Instant.now().minus(2, ChronoUnit.HOURS);

            String eskiToken = Jwts.builder()
                    .subject("g.personel")
                    .claim("rol", Rol.PERSONEL.name())
                    .issuedAt(Date.from(gecmis))
                    .expiration(Date.from(gecmis.plus(1, ChronoUnit.HOURS)))
                    .signWith(anahtar)
                    .compact();

            mockMvc.perform(get("/api/v1/talepler").header("Authorization", bearer(eskiToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Baska anahtarla imzalanmis token 401")
        void baskaAnahtarlaImzali401() throws Exception {
            SecretKey sahteAnahtar = Keys.hmacShaKeyFor(
                    "saldirganin-uydurdugu-en-az-256-bit-uzunlugundaki-anahtar".getBytes(StandardCharsets.UTF_8));

            String sahteToken = Jwts.builder()
                    .subject("g.yonetici")
                    .claim("rol", Rol.YONETICI.name())
                    .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                    .signWith(sahteAnahtar)
                    .compact();

            mockMvc.perform(get("/api/v1/raporlar/ozet").header("Authorization", bearer(sahteToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Yanlis sifre 401, kullanici adinin varligi sizmaz")
        void yanlisSifre401() throws Exception {
            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "g.personel", "sifre": "yanlis"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mesaj").value("Kullanici adi veya sifre hatali"));
        }

        @Test
        @DisplayName("Olmayan kullanici da ayni mesaji alir")
        void olmayanKullaniciAyniMesaj() throws Exception {
            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "boyle.biri.yok", "sifre": "Parola123!"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mesaj").value("Kullanici adi veya sifre hatali"));
        }

        @Test
        @DisplayName("Pasif kullanici giris yapamaz")
        void pasifKullaniciGirisYapamaz() throws Exception {
            Kullanici personel = kullaniciRepository.findByKullaniciAdi("g.personel").orElseThrow();
            personel.setAktif(false);
            kullaniciRepository.saveAndFlush(personel);

            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "g.personel", "sifre": "%s"}
                                    """.formatted(SIFRE)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- rol bazli yetki ------------------------------------------------

    @Nested
    @DisplayName("Rol bazli yetki")
    class RolYetkisi {

        @Test
        @DisplayName("PERSONEL rapor ucuna erisemez")
        void personelRaporGoremez() throws Exception {
            mockMvc.perform(get("/api/v1/raporlar/ozet").header("Authorization", bearer(personelToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.kod").value("YETKISIZ_ISLEM"));
        }

        @Test
        @DisplayName("AMIR rapor ucuna erisemez")
        void amirRaporGoremez() throws Exception {
            mockMvc.perform(get("/api/v1/raporlar/ozet").header("Authorization", bearer(amirToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("YONETICI rapor ucunu gorur")
        void yoneticiRaporGorur() throws Exception {
            mockMvc.perform(get("/api/v1/raporlar/ozet").header("Authorization", bearer(yoneticiToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toplamTalep").value(3));
        }

        @Test
        @DisplayName("PERSONEL karar veremez")
        void personelKararVeremez() throws Exception {
            mockMvc.perform(post("/api/v1/talepler/" + personelTalebiId + "/karar")
                            .header("Authorization", bearer(personelToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"karar": "ONAYLA", "aciklama": "kendim onayliyorum"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("YONETICI karar veremez, sadece izler")
        void yoneticiKararVeremez() throws Exception {
            mockMvc.perform(post("/api/v1/talepler/" + personelTalebiId + "/karar")
                            .header("Authorization", bearer(yoneticiToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"karar": "ONAYLA", "aciklama": "uygundur"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AMIR talep olusturamaz")
        void amirTalepOlusturamaz() throws Exception {
            mockMvc.perform(post("/api/v1/talepler")
                            .header("Authorization", bearer(amirToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"baslik": "Amir talebi", "aciklama": "aciklama", "tur": "DIGER"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PERSONEL talep olusturabilir")
        void personelTalepOlusturur() throws Exception {
            mockMvc.perform(post("/api/v1/talepler")
                            .header("Authorization", bearer(personelToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"baslik": "Yeni talep", "aciklama": "aciklama metni", "tur": "DIGER"}
                                    """))
                    .andExpect(status().isCreated());
        }
    }

    // --- kayit bazli yetki ----------------------------------------------

    @Nested
    @DisplayName("Kayit bazli yetki")
    class KayitYetkisi {

        @Test
        @DisplayName("Personel baskasinin talebini goremez")
        void personelBaskasininTalebiniGoremez() throws Exception {
            mockMvc.perform(get("/api/v1/talepler/" + digerPersonelTalebiId)
                            .header("Authorization", bearer(personelToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Personel kendi talebini gorur")
        void personelKendiTalebiniGorur() throws Exception {
            mockMvc.perform(get("/api/v1/talepler/" + personelTalebiId)
                            .header("Authorization", bearer(personelToken)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Personel listesinde yalnizca kendi talepleri var")
        void personelListesiKendiTalepleri() throws Exception {
            mockMvc.perform(get("/api/v1/talepler").header("Authorization", bearer(personelToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toplamKayit").value(1))
                    .andExpect(jsonPath("$.icerik[0].baslik").value("Personel talebi"));
        }

        @Test
        @DisplayName("Personel birimId parametresiyle kapsamini genisletemez")
        void personelBirimFiltresiyleKacamaz() throws Exception {
            // Istemciden gelen birimId personel icin yok sayiliyor. Sayilsaydi
            // burada 3 talep donerdi ve bu bir yetki acigi olurdu.
            mockMvc.perform(get("/api/v1/talepler?birimId=1").header("Authorization", bearer(personelToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toplamKayit").value(1));
        }

        @Test
        @DisplayName("Amir baska birimin talebini goremez")
        void amirBaskaBiriminTalebiniGoremez() throws Exception {
            mockMvc.perform(get("/api/v1/talepler/" + baskaBirimTalebiId)
                            .header("Authorization", bearer(amirToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Amir baska birimin talebine karar veremez")
        void amirBaskaBiriminTalebineKararVeremez() throws Exception {
            mockMvc.perform(post("/api/v1/talepler/" + baskaBirimTalebiId + "/karar")
                            .header("Authorization", bearer(amirToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"karar": "ONAYLA", "aciklama": "uygundur"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Amir kendi birimindeki talebi onaylar")
        void amirKendiBiriminiOnaylar() throws Exception {
            mockMvc.perform(post("/api/v1/talepler/" + personelTalebiId + "/karar")
                            .header("Authorization", bearer(amirToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"karar": "ONAYLA", "aciklama": "uygundur"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.durum").value("ONAYLANDI"));
        }

        @Test
        @DisplayName("Personel baskasinin talebini onaya gonderemez")
        void baskasininTalebiniOnayaGonderemez() throws Exception {
            mockMvc.perform(post("/api/v1/talepler/" + digerPersonelTalebiId + "/onaya-gonder")
                            .header("Authorization", bearer(personelToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Personel baskasinin talebini guncelleyemez")
        void baskasininTalebiniGuncelleyemez() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/talepler/" + digerPersonelTalebiId)
                            .header("Authorization", bearer(digerPersonelToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"baslik": "Benim", "aciklama": "benim aciklamam", "tur": "DIGER"}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/talepler/" + digerPersonelTalebiId)
                            .header("Authorization", bearer(personelToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"baslik": "Kaciriyorum", "aciklama": "baskasinin talebi", "tur": "DIGER"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Yonetici tum birimleri gorur")
        void yoneticiHepsiniGorur() throws Exception {
            mockMvc.perform(get("/api/v1/talepler").header("Authorization", bearer(yoneticiToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toplamKayit").value(3));
        }

        @Test
        @DisplayName("Baska birimin amiri o birimin listesini goremez")
        void baskaBirimAmiriListeyiGoremez() throws Exception {
            mockMvc.perform(get("/api/v1/talepler").header("Authorization", bearer(baskaBirimAmirToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toplamKayit").value(1))
                    .andExpect(jsonPath("$.icerik[0].baslik").value("Muhasebe talebi"));
        }
    }

    @Nested
    @DisplayName("Kaba kuvvet korumasi")
    class KabaKuvvet {

        @Test
        @DisplayName("Ard arda basarisiz denemeden sonra dogru sifre bile 429 alir")
        void limitAsilincaKilitlenir() throws Exception {
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/kimlik/giris")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"kullaniciAdi": "g.personel", "sifre": "yanlis%d"}
                                        """.formatted(i)))
                        .andExpect(status().isUnauthorized());
            }

            // Alti deneme, bu kez sifre DOGRU. Yine de kabul edilmemeli.
            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "g.personel", "sifre": "%s"}
                                    """.formatted(SIFRE)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.kod").value("COK_FAZLA_DENEME"));
        }

        @Test
        @DisplayName("Limit kullanici bazinda: bir kullanicinin kilidi digerini etkilemez")
        void kilitKullaniciBazinda() throws Exception {
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/kimlik/giris")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"kullaniciAdi": "g.diger", "sifre": "yanlis%d"}
                                        """.formatted(i)))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "g.amir", "sifre": "%s"}
                                    """.formatted(SIFRE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Basarili giris sayaci sifirliyor")
        void basariliGirisSayaciSifirlar() throws Exception {
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/v1/kimlik/giris")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"kullaniciAdi": "g.yonetici", "sifre": "yanlis%d"}
                                        """.formatted(i)))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/api/v1/kimlik/giris")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"kullaniciAdi": "g.yonetici", "sifre": "%s"}
                                    """.formatted(SIFRE)))
                    .andExpect(status().isOk());

            // Sayac sifirlandigi icin dort yanlis daha kilitlemiyor.
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/v1/kimlik/giris")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"kullaniciAdi": "g.yonetici", "sifre": "yine-yanlis%d"}
                                        """.formatted(i)))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    // --- acik uclar -----------------------------------------------------

    @Nested
    @DisplayName("Kasitli olarak acik uclar")
    class AcikUclar {

        @Test
        @DisplayName("Saglik ucu tokensiz erisilebilir")
        void saglikAcik() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Metrics ucu tokensiz erisilemez")
        void metricsKapali() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Metrics ucunu yalnizca yonetici gorur")
        void metricsYalnizcaYonetici() throws Exception {
            mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(personelToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(yoneticiToken)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("API dokumani tokensiz erisilebilir")
        void apiDokumaniAcik() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }
}
