package tr.ebrar.talep.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import tr.ebrar.talep.destek.VeritabaniTestTemeli;

@SpringBootTest
@AutoConfigureMockMvc
class KorelasyonKimligiFiltresiTest extends VeritabaniTestTemeli {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Kimlik gonderilmezse uretilir ve yanit basliginda doner")
    void kimlikUretilir() throws Exception {
        String kimlik = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(KorelasyonKimligiFiltresi.BASLIK))
                .andReturn().getResponse().getHeader(KorelasyonKimligiFiltresi.BASLIK);

        assertThat(kimlik).isNotBlank();
    }

    @Test
    @DisplayName("Istemcinin gonderdigi kimlik korunur")
    void istemciKimligiKorunur() throws Exception {
        mockMvc.perform(get("/actuator/health").header(KorelasyonKimligiFiltresi.BASLIK, "on-yuz-123"))
                .andExpect(header().string(KorelasyonKimligiFiltresi.BASLIK, "on-yuz-123"));
    }

    @Test
    @DisplayName("Asiri uzun kimlik yok sayilir, yerine yenisi uretilir")
    void asiriUzunKimlikReddedilir() throws Exception {
        String cokUzun = "x".repeat(500);

        String donen = mockMvc.perform(get("/actuator/health").header(KorelasyonKimligiFiltresi.BASLIK, cokUzun))
                .andReturn().getResponse().getHeader(KorelasyonKimligiFiltresi.BASLIK);

        assertThat(donen).isNotEqualTo(cokUzun).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    @DisplayName("Kimliksiz erisimde de kimlik uretiliyor: hata loglari da izlenebilir")
    void yetkisizIstekteDeKimlikVar() throws Exception {
        mockMvc.perform(get("/api/talepler"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(KorelasyonKimligiFiltresi.BASLIK));
    }
}
