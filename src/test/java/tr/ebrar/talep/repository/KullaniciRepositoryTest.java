package tr.ebrar.talep.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@VeritabaniTesti
class KullaniciRepositoryTest extends VeritabaniTestTemeli {

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private EntityManager entityManager;

    private Birim birim;

    @BeforeEach
    void hazirla() {
        birim = birimRepository.save(VeriUretici.birim("KUL"));
        kullaniciRepository.save(VeriUretici.kullanici("ayse", Rol.PERSONEL, birim));
        kullaniciRepository.save(VeriUretici.kullanici("veli", Rol.AMIR, birim));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Kullanici adiyla arama birimi ayni sorguda getirir")
    void kullaniciAdiylaAramaBirimiYuklerGetirir() {
        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi("ayse").orElseThrow();

        assertThat(kullanici.getRol()).isEqualTo(Rol.PERSONEL);
        assertThat(Hibernate.isInitialized(kullanici.getBirim()))
                .as("giris akisinda ikinci sorgu olmamali")
                .isTrue();
        assertThat(kullanici.getBirim().getKod()).isEqualTo("KUL");
    }

    @Test
    @DisplayName("Olmayan kullanici adi bos Optional doner")
    void olmayanKullaniciBosDoner() {
        assertThat(kullaniciRepository.findByKullaniciAdi("yok")).isEmpty();
    }

    @Test
    @DisplayName("Birim ve role gore aktif kullanicilar bulunur")
    void birimVeRoleGoreArama() {
        assertThat(kullaniciRepository.findByBirimIdAndRolAndAktifTrue(birim.getId(), Rol.AMIR))
                .extracting(Kullanici::getKullaniciAdi)
                .containsExactly("veli");
    }

    @Test
    @DisplayName("Pasif kullanici amir aramasina dahil edilmez")
    void pasifKullaniciHaricTutulur() {
        Kullanici veli = kullaniciRepository.findByKullaniciAdi("veli").orElseThrow();
        veli.setAktif(false);
        kullaniciRepository.saveAndFlush(veli);

        assertThat(kullaniciRepository.findByBirimIdAndRolAndAktifTrue(birim.getId(), Rol.AMIR)).isEmpty();
    }

    @Test
    @DisplayName("Ayni kullanici adi ikinci kez kaydedilemez")
    void kullaniciAdiTekilligiKorunur() {
        Kullanici kopya = VeriUretici.kullanici("ayse", Rol.PERSONEL, birim);
        kopya.setEposta("baska@ornek.gov.tr");

        assertThatThrownBy(() -> kullaniciRepository.saveAndFlush(kopya))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Ayni eposta ikinci kez kaydedilemez")
    void epostaTekilligiKorunur() {
        Kullanici kopya = VeriUretici.kullanici("baskaad", Rol.PERSONEL, birim);
        kopya.setEposta("ayse@ornek.gov.tr");

        assertThatThrownBy(() -> kullaniciRepository.saveAndFlush(kopya))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Birim kodu tekil")
    void birimKoduTekil() {
        assertThat(birimRepository.existsByKod("KUL")).isTrue();

        assertThatThrownBy(() -> birimRepository.saveAndFlush(VeriUretici.birim("KUL")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
