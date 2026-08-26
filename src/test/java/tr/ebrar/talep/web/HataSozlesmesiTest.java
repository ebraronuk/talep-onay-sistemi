package tr.ebrar.talep.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tr.ebrar.talep.destek.VeritabaniTestTemeli;

/**
 * Cerceve kaynakli hatalarin da bizim sozlesmemize uydugunu dogrular.
 *
 * <p>Bu sinif bir hatanin ardindan yazildi. Once {@code @ExceptionHandler(Exception.class)}
 * ile genel bir yakalayici koymustum. Spring'in {@code ExceptionHandlerExceptionResolver}'i
 * cercevenin kendi {@code DefaultHandlerExceptionResolver}'indan once calistigi icin,
 * bilinmeyen bir yol istegi 404 yerine 500 donuyordu. Yani "sayfa yok" hatasi
 * "sunucu coktu" gibi gorunuyordu.
 *
 * <p>{@code HataYakalayici} artik {@code ResponseEntityExceptionHandler}'i genisletiyor.
 * Buradaki testler o davranisi kilitliyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HataSozlesmesiTest extends VeritabaniTestTemeli {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Bilinmeyen yol 404 doner, 500 degil")
    void bilinmeyenYol404() throws Exception {
        mockMvc.perform(get("/api/boyle-bir-uc-yok").with(user("biri").roles("PERSONEL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.kod").value("KAYNAK_BULUNAMADI"))
                .andExpect(jsonPath("$.mesaj").exists())
                .andExpect(jsonPath("$.zaman").exists());
    }

    @Test
    @DisplayName("Desteklenmeyen HTTP metodu 405 doner")
    void desteklenmeyenMetot405() throws Exception {
        mockMvc.perform(delete("/api/talepler").with(user("biri").roles("PERSONEL")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.kod").value("DESTEKLENMEYEN_METOT"));
    }

    @Test
    @DisplayName("Desteklenmeyen icerik tipi 415 doner")
    void desteklenmeyenIcerikTipi415() throws Exception {
        mockMvc.perform(post("/api/talepler")
                        .with(user("biri").roles("PERSONEL"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("duz metin"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.kod").value("DESTEKLENMEYEN_ICERIK_TIPI"));
    }

    @Test
    @DisplayName("Bozuk JSON govdesi 400 doner ve ic detay sizmaz")
    void bozukGovde400() throws Exception {
        mockMvc.perform(post("/api/talepler")
                        .with(user("biri").roles("PERSONEL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ bu gecerli json degil "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_GOVDE"))
                // Jackson'in ic mesaji (sinif adlari, satir/sutun bilgisi) disariya cikmamali
                .andExpect(jsonPath("$.mesaj").value("Istek govdesi okunamadi"));
    }

    @Test
    @DisplayName("Yol degiskeni beklenen tipte degilse 400 doner")
    void gecersizYolDegiskeni400() throws Exception {
        mockMvc.perform(get("/api/talepler/abc").with(user("biri").roles("PERSONEL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("GECERSIZ_PARAMETRE"))
                .andExpect(jsonPath("$.mesaj").value("'id' parametresi beklenen tipte degil"));
    }

    @Test
    @DisplayName("Hata govdesi her durumda ayni dort alani tasir")
    void sozlesmeTutarli() throws Exception {
        mockMvc.perform(get("/api/talepler/999999").with(user("biri").roles("PERSONEL")))
                .andExpect(jsonPath("$.kod").exists())
                .andExpect(jsonPath("$.mesaj").exists())
                .andExpect(jsonPath("$.detaylar").isArray())
                .andExpect(jsonPath("$.zaman").exists());
    }
}
