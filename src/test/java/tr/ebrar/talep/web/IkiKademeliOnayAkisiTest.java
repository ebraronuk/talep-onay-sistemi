package tr.ebrar.talep.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;

/**
 * Iki kademeli onay akisinin uctan uca dogrulanmasi.
 *
 * <p>Servis testleri kurallari tek tek kanitliyor; bu test butunun calistigini
 * gosteriyor: HTTP, JWT, yetki, durum makinesi, denetim izi ve bildirim birlikte.
 *
 * <p>Ayni senaryo mulakatta ekrandan gosterilecek olan senaryo: personel yuksek
 * tutarli bir talep aciyor, birim amiri onayliyor ama is bitmiyor, yonetici
 * ikinci kademede sonuclandiriyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IkiKademeliOnayAkisiTest extends VeritabaniTestTemeli {

    private static final String SIFRE = "Parola123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private PasswordEncoder sifreKodlayici;

    private String personelToken;
    private String amirToken;
    private String yoneticiToken;
    private Long yoneticiId;

    @BeforeEach
    void hazirla() throws Exception {
        veritabaniTemizleyici.hepsiniTemizle();

        Birim birim = birimRepository.save(new Birim("AKS", "Akis testi birimi"));
        kaydet("akis.personel", Rol.PERSONEL, birim);
        kaydet("akis.amir", Rol.AMIR, birim);
        yoneticiId = kaydet("akis.yonetici", Rol.YONETICI, birim).getId();

        personelToken = girisYap("akis.personel");
        amirToken = girisYap("akis.amir");
        yoneticiToken = girisYap("akis.yonetici");
    }

    @Test
    @DisplayName("Yuksek tutarli talep iki kademeden gecerek onaylaniyor")
    void yuksekTutarliTalepIkiKademedenGecer() throws Exception {
        // 1. Personel limit ustunde bir talep aciyor
        Long talepId = talepAc("Sunucu yenileme", "120000.00");

        detay(talepId, personelToken).andExpect(jsonPath("$.durum").value("TASLAK"));

        // 2. Onaya gonderiyor
        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/onaya-gonder")
                        .header("Authorization", "Bearer " + personelToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("BEKLEMEDE"));

        // 3. Birim amiri onayliyor: is BITMIYOR, ikinci kademeye dusuyor
        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/karar")
                        .header("Authorization", "Bearer " + amirToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "Birim ihtiyaci uygun"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("YONETICI_ONAYINDA"));

        // 4. Yoneticiye bildirim dusmus olmali
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(bildirimRepository.countByAliciIdAndOkunduFalse(yoneticiId)).isEqualTo(1));

        // 5. Yonetici sonuclandiriyor
        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/karar")
                        .header("Authorization", "Bearer " + yoneticiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "Butcede yeri var"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        // 6. Denetim izi dort adimin hepsini tasimali
        JsonNode govde = json.readTree(
                detay(talepId, personelToken).andReturn().getResponse().getContentAsString());
        JsonNode gecmis = govde.get("gecmis");

        assertThat(gecmis).hasSize(4);
        assertThat(gecmis.get(0).get("yeniDurum").asText()).isEqualTo("TASLAK");
        assertThat(gecmis.get(1).get("yeniDurum").asText()).isEqualTo("BEKLEMEDE");
        assertThat(gecmis.get(2).get("yeniDurum").asText()).isEqualTo("YONETICI_ONAYINDA");
        assertThat(gecmis.get(3).get("yeniDurum").asText()).isEqualTo("ONAYLANDI");

        // Her adimda kimin islem yaptigi kayitli: denetim izinin asil degeri bu.
        assertThat(gecmis.get(2).get("islemYapanAdSoyad").asText()).contains("akis.amir");
        assertThat(gecmis.get(3).get("islemYapanAdSoyad").asText()).contains("akis.yonetici");
    }

    @Test
    @DisplayName("Dusuk tutarli talep tek kademede sonuclaniyor")
    void dusukTutarliTalepTekKademe() throws Exception {
        Long talepId = talepAc("Klavye", "850.00");

        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/onaya-gonder")
                        .header("Authorization", "Bearer " + personelToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/karar")
                        .header("Authorization", "Bearer " + amirToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "Uygundur"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("ONAYLANDI"));
    }

    @Test
    @DisplayName("Ikinci kademedeki talebe amir karisamaz")
    void ikinciKademeyeAmirKarisamaz() throws Exception {
        Long talepId = talepAc("Veri merkezi bakimi", "300000.00");

        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/onaya-gonder")
                        .header("Authorization", "Bearer " + personelToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/karar")
                        .header("Authorization", "Bearer " + amirToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "uygun"}
                                """))
                .andExpect(jsonPath("$.durum").value("YONETICI_ONAYINDA"));

        // Amir ikinci kez, bu kez ikinci kademede karar vermeye calisiyor
        mockMvc.perform(post("/api/v1/talepler/" + talepId + "/karar")
                        .header("Authorization", "Bearer " + amirToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "yine ben"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.kod").value("YETKISIZ_ISLEM"));
    }

    @Test
    @DisplayName("Negatif tutar dogrulamadan geciyor mu")
    void negatifTutarReddedilir() throws Exception {
        mockMvc.perform(post("/api/v1/talepler")
                        .header("Authorization", "Bearer " + personelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "Hatali", "aciklama": "negatif tutar", "tur": "SATIN_ALMA", "tutar": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA_HATASI"))
                .andExpect(jsonPath("$.detaylar[0].alan").value("tutar"));
    }

    // --- yardimcilar ----------------------------------------------------

    private Long talepAc(String baslik, String tutar) throws Exception {
        String govde = mockMvc.perform(post("/api/v1/talepler")
                        .header("Authorization", "Bearer " + personelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "%s", "aciklama": "Akis testi", "tur": "SATIN_ALMA", "tutar": %s}
                                """.formatted(baslik, tutar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(govde).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions detay(Long talepId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/talepler/" + talepId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Kullanici kaydet(String kullaniciAdi, Rol rol, Birim birim) {
        return kullaniciRepository.save(new Kullanici(
                kullaniciAdi, kullaniciAdi, kullaniciAdi + "@ornek.gov.tr",
                sifreKodlayici.encode(SIFRE), rol, birim));
    }

    private String girisYap(String kullaniciAdi) throws Exception {
        String govde = mockMvc.perform(post("/api/v1/kimlik/giris")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kullaniciAdi": "%s", "sifre": "%s"}
                                """.formatted(kullaniciAdi, SIFRE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(govde).get("token").asText();
    }
}
