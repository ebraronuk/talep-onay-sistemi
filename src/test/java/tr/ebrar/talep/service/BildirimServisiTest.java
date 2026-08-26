package tr.ebrar.talep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Bildirim;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.hata.YetkisizIslemException;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepRepository;

@SpringBootTest
class BildirimServisiTest extends VeritabaniTestTemeli {

    @Autowired
    private BildirimServisi bildirimServisi;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private OnayKaydiRepository onayKaydiRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    private Long benimBildirimim;
    private Long baskasininBildirimi;

    @BeforeEach
    void hazirla() {
        temizle();

        Birim birim = birimRepository.save(VeriUretici.birim("BLD"));
        Kullanici ben = kullaniciRepository.save(VeriUretici.kullanici("bld.ben", Rol.PERSONEL, birim));
        Kullanici baskasi = kullaniciRepository.save(VeriUretici.kullanici("bld.baskasi", Rol.PERSONEL, birim));

        Talep talep = talepRepository.save(VeriUretici.talep("Bildirim testi talebi", ben));

        benimBildirimim = bildirimRepository.save(new Bildirim(ben, talep, "Talebiniz onaylandi")).getId();
        bildirimRepository.save(new Bildirim(ben, talep, "Talebiniz guncellendi"));
        baskasininBildirimi = bildirimRepository.save(new Bildirim(baskasi, talep, "Baskasinin bildirimi")).getId();
    }

    @AfterEach
    void temizle() {
        veritabaniTemizleyici.hepsiniTemizle();
    }

    @Test
    @DisplayName("Kullanici yalnizca kendi bildirimlerini gorur")
    void kendiBildirimleri() {
        var sayfa = bildirimServisi.bildirimlerim("bld.ben", PageRequest.of(0, 10));

        assertThat(sayfa.getTotalElements()).isEqualTo(2);
        assertThat(sayfa.getContent()).extracting("mesaj")
                .doesNotContain("Baskasinin bildirimi");
    }

    @Test
    @DisplayName("Okunmamis sayisi dogru")
    void okunmamisSayisi() {
        assertThat(bildirimServisi.okunmamisSayisi("bld.ben")).isEqualTo(2);

        bildirimServisi.okunduIsaretle(benimBildirimim, "bld.ben");

        assertThat(bildirimServisi.okunmamisSayisi("bld.ben")).isEqualTo(1);
    }

    @Test
    @DisplayName("Baskasinin bildirimi okundu isaretlenemez")
    void baskasininBildirimiIsaretlenemez() {
        assertThatThrownBy(() -> bildirimServisi.okunduIsaretle(baskasininBildirimi, "bld.ben"))
                .isInstanceOf(YetkisizIslemException.class);

        assertThat(bildirimRepository.findById(baskasininBildirimi).orElseThrow().isOkundu()).isFalse();
    }
}
