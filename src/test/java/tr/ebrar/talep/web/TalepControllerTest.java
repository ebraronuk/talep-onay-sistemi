package tr.ebrar.talep.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import tr.ebrar.talep.destek.SahteKimlik;
import tr.ebrar.talep.domain.GecersizDurumGecisiException;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.hata.GecersizIslemException;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.hata.YetkisizIslemException;
import tr.ebrar.talep.service.TalepServisi;
import tr.ebrar.talep.service.dto.KullaniciOzetDto;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.dto.TalepOzetDto;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;

/**
 * HTTP katmani testleri. Servis mock; burada test edilen sey ucun kendisi:
 * dogru status kodu doner mu, hata sozlesmesi tutuyor mu, dogrulama devrede mi.
 *
 * <p>Guvenlik filtreleri kapali. Rol bazli yetki kontrolu gercek zincirle
 * {@code GuvenlikEntegrasyonTest} icinde test ediliyor; ikisini ayni yerde
 * test etmek her iki testi de bulaniklastirirdi.
 */
@WebMvcTest(controllers = TalepController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(HataYakalayici.class)
class TalepControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper jsonYazici;

    @MockitoBean
    private TalepServisi talepServisi;

    // @WebMvcTest, Filter tipindeki bean'leri dilime dahil ediyor; JwtKimlikFiltresi de
    // oyle. Kendi bagimliligi (JwtUretici) bu dilimde olmadigi icin baglam acilmiyordu.
    // Filtreler zaten kapali oldugundan sahtesi yeterli.
    @MockitoBean
    private tr.ebrar.talep.security.JwtKimlikFiltresi jwtKimlikFiltresi;

    private static final TalepDetayDto ORNEK_DETAY = new TalepDetayDto(
            7L, "Ergonomik sandalye", "Bel agrisi nedeniyle", TalepTuru.DONANIM, TalepDurumu.TASLAK,
            new KullaniciOzetDto(1L, "ayse", "Ayse Yilmaz", Rol.PERSONEL, "BTGM"),
            "BTGM", "Bilgi Teknolojileri", Instant.now(), Instant.now(), List.of());

    @Test
    @DisplayName("POST /api/v1/talepler 201 doner ve Location basligi verir")
    void olusturma201() throws Exception {
        when(talepServisi.olustur(any(TalepOlusturKomutu.class), eq("ayse"))).thenReturn(ORNEK_DETAY);

        mockMvc.perform(post("/api/v1/talepler")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonYazici.writeValueAsString(
                                new TalepOlusturKomutu("Ergonomik sandalye", "Bel agrisi nedeniyle", TalepTuru.DONANIM))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/talepler/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.durum").value("TASLAK"));
    }

    @Test
    @DisplayName("Bos baslik 400 ve alan bazli detay doner")
    void dogrulamaHatasi400() throws Exception {
        mockMvc.perform(post("/api/v1/talepler")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "", "aciklama": "", "tur": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA_HATASI"))
                .andExpect(jsonPath("$.detaylar.length()").value(3))
                .andExpect(jsonPath("$.zaman").exists());
    }

    @Test
    @DisplayName("Baslik 200 karakteri asarsa 400")
    void uzunBaslik400() throws Exception {
        String uzun = "a".repeat(201);

        mockMvc.perform(post("/api/v1/talepler")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "%s", "aciklama": "gecerli aciklama", "tur": "DIGER"}
                                """.formatted(uzun)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detaylar[0].alan").value("baslik"));
    }

    @Test
    @DisplayName("Bozuk JSON 400 doner, ic detay sizmaz")
    void bozukGovde400() throws Exception {
        mockMvc.perform(post("/api/v1/talepler")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ bu gecerli json degil "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_GOVDE"))
                .andExpect(jsonPath("$.mesaj").value("Istek govdesi okunamadi"));
    }

    @Test
    @DisplayName("Tanimsiz enum degeri 400 doner")
    void gecersizEnum400() throws Exception {
        mockMvc.perform(post("/api/v1/talepler")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "Baslik", "aciklama": "Aciklama", "tur": "UCAK_BILETI"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_GOVDE"));
    }

    @Test
    @DisplayName("Olmayan talep 404 doner")
    void bulunamadi404() throws Exception {
        when(talepServisi.detay(eq(999L), anyString()))
                .thenThrow(new KayitBulunamadiException("Talep", 999L));

        mockMvc.perform(get("/api/v1/talepler/999").principal(SahteKimlik.olarak("ayse", Rol.PERSONEL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.kod").value("KAYIT_BULUNAMADI"));
    }

    @Test
    @DisplayName("Baskasinin talebi 403 doner")
    void yetkisiz403() throws Exception {
        when(talepServisi.detay(anyLong(), anyString()))
                .thenThrow(new YetkisizIslemException("Bu talebi goruntuleme yetkiniz yok"));

        mockMvc.perform(get("/api/v1/talepler/5").principal(SahteKimlik.olarak("ayse", Rol.PERSONEL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.kod").value("YETKISIZ_ISLEM"));
    }

    @Test
    @DisplayName("Gecersiz durum gecisi 409 doner")
    void gecersizGecis409() throws Exception {
        when(talepServisi.karar(anyLong(), any(OnayKarariKomutu.class), anyString()))
                .thenThrow(new GecersizDurumGecisiException(TalepDurumu.TASLAK, TalepDurumu.ONAYLANDI));

        mockMvc.perform(post("/api/v1/talepler/1/karar")
                        .principal(SahteKimlik.olarak("veli", Rol.AMIR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "ONAYLA", "aciklama": "olur"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_DURUM_GECISI"));
    }

    @Test
    @DisplayName("Is kurali ihlali 400 doner")
    void gecersizIslem400() throws Exception {
        when(talepServisi.karar(anyLong(), any(OnayKarariKomutu.class), anyString()))
                .thenThrow(new GecersizIslemException("Ret islemi icin gerekce yazmak zorunlu"));

        mockMvc.perform(post("/api/v1/talepler/1/karar")
                        .principal(SahteKimlik.olarak("veli", Rol.AMIR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"karar": "REDDET", "aciklama": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_ISLEM"));
    }

    @Test
    @DisplayName("Id yerine metin gelirse 400 doner")
    void gecersizPathParametresi400() throws Exception {
        mockMvc.perform(get("/api/v1/talepler/abc").principal(SahteKimlik.olarak("ayse", Rol.PERSONEL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_PARAMETRE"));
    }

    @Test
    @DisplayName("Listeleme kendi sayfa sozlesmemizi doner, Spring'in Page'ini degil")
    void listelemeSayfaSozlesmesi() throws Exception {
        var icerik = List.of(new TalepOzetDto(1L, "Ilk talep", TalepTuru.IZIN, TalepDurumu.BEKLEMEDE,
                "Ayse Yilmaz", "BTGM", Instant.now()));
        when(talepServisi.listele(any(), any(), anyString()))
                .thenReturn(new PageImpl<>(icerik, PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/talepler?durum=BEKLEMEDE")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.sayfaNo").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(20))
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.sonSayfaMi").value(true))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    @DisplayName("Guncelleme 200 doner")
    void guncelleme200() throws Exception {
        when(talepServisi.guncelle(anyLong(), any(), anyString())).thenReturn(ORNEK_DETAY);

        mockMvc.perform(put("/api/v1/talepler/7")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baslik": "Guncel", "aciklama": "Guncel aciklama", "tur": "DIGER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("Onaya gonderme 200 doner")
    void onayaGonderme200() throws Exception {
        when(talepServisi.onayaGonder(eq(7L), anyString())).thenReturn(ORNEK_DETAY);

        mockMvc.perform(post("/api/v1/talepler/7/onaya-gonder")
                        .principal(SahteKimlik.olarak("ayse", Rol.PERSONEL)))
                .andExpect(status().isOk());
    }
}
