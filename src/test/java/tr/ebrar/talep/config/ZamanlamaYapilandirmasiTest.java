package tr.ebrar.talep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.service.TalepServisi;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;

/**
 * Isleyici gercekten kendiliginden calisiyor mu.
 *
 * <p>Diger testler turu elle kosuyor; zamanlama anotasyonu ya da ayari silinse hepsi
 * yesil kalir, uretimde ise hicbir bildirim cikmazdi. Aralik application.yml'den
 * geliyor, burada ezilmiyor: oradaki degerin gecersiz olmasi da yakalansin.
 *
 * <p>{@code @DirtiesContext}: baglam kapanmazsa zamanlayicisi sonraki test siniflarinin
 * veritabaninda calismaya devam ederdi.
 */
@SpringBootTest(properties = "talep.giden-olay.zamanlama-etkin=true")
@DirtiesContext
class ZamanlamaYapilandirmasiTest extends VeritabaniTestTemeli {

    @Autowired
    private TalepServisi talepServisi;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    private Kullanici amir;

    @BeforeEach
    void hazirla() {
        veritabaniTemizleyici.hepsiniTemizle();

        Birim birim = birimRepository.save(VeriUretici.birim("ZMN"));
        kullaniciRepository.save(VeriUretici.kullanici("zmnpersonel", Rol.PERSONEL, birim));
        amir = kullaniciRepository.save(VeriUretici.kullanici("zmnamir", Rol.AMIR, birim));
    }

    @AfterEach
    void temizle() {
        veritabaniTemizleyici.hepsiniTemizle();
    }

    @Test
    @DisplayName("Zamanlayici bildirimi kimse tetiklemeden uretir")
    void zamanlayiciKendiligindenCalisir() {
        Long talepId = talepServisi.olustur(
                new TalepOlusturKomutu("Klavye talebi", "aciklama", TalepTuru.DONANIM, null), "zmnpersonel").id();
        talepServisi.onayaGonder(talepId, "zmnpersonel");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(bildirimRepository.countByAliciIdAndOkunduFalse(amir.getId())).isEqualTo(1));
    }
}
