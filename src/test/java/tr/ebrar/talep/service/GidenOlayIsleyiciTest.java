package tr.ebrar.talep.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.GidenOlay;
import tr.ebrar.talep.domain.GidenOlayDurumu;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.GidenOlayRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;

/**
 * Kutudaki olayin bildirime donusmesi ve donusemedigi haller.
 *
 * <p>Zamanlayici test profilinde kapali, turlar elle kosuluyor: arka planda calisan bir
 * zamanlayici varken "bu turda islenmedi" iddiasi kurulamazdi.
 */
@SpringBootTest
class GidenOlayIsleyiciTest extends VeritabaniTestTemeli {

    @Autowired
    private GidenOlayIsleyici isleyici;

    @Autowired
    private TalepServisi talepServisi;

    @Autowired
    private GidenOlayRepository gidenOlayRepository;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private TransactionTemplate islem;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    private Kullanici amir;
    private Kullanici ikinciAmir;

    @BeforeEach
    void hazirla() {
        veritabaniTemizleyici.hepsiniTemizle();

        Birim birim = birimRepository.save(VeriUretici.birim("ISL"));
        kullaniciRepository.save(VeriUretici.kullanici("islpersonel", Rol.PERSONEL, birim));
        amir = kullaniciRepository.save(VeriUretici.kullanici("islamir", Rol.AMIR, birim));
        ikinciAmir = kullaniciRepository.save(VeriUretici.kullanici("islamir2", Rol.AMIR, birim));
    }

    @AfterEach
    void temizle() {
        engelleriKaldir();
        veritabaniTemizleyici.hepsiniTemizle();
    }

    @Test
    @DisplayName("Commit olmus ama islenmemis olay sonraki turda bildirime donusur")
    void islenmemisOlaySonrakiTurdaIslenir() {
        onayaGonder("Klavye talebi");
        assertThat(bildirimRepository.count()).as("tur kosmadan bildirim olmamali").isZero();

        // Uygulama burada kapanip acilsa da sonuc ayni: olayi tutan tek yer veritabani.
        isleyici.bekleyenleriIsle();

        assertThat(bildirimSayisi(amir)).isEqualTo(1);
        assertThat(bildirimSayisi(ikinciAmir)).isEqualTo(1);
        assertThat(tekOlay().getDurum()).isEqualTo(GidenOlayDurumu.ISLENDI);
        assertThat(tekOlay().getIslenmeTarihi()).isNotNull();
    }

    @Test
    @DisplayName("Islenmis olay sonraki turda tekrar bildirim uretmez")
    void islenmisOlayTekrarlanmaz() {
        onayaGonder("Klavye talebi");

        isleyici.bekleyenleriIsle();
        isleyici.bekleyenleriIsle();

        assertThat(bildirimSayisi(amir)).isEqualTo(1);
    }

    @Test
    @DisplayName("Bildirim yazilamazsa durum degisikligi geri sarmaz, olay beklemede kalir")
    void bildirimYazilamazsaOlayBekler() {
        Long talepId = onayaGonder("Klavye talebi");
        yazimiEngelle("bildirim", "false");

        isleyici.bekleyenleriIsle();

        assertThat(talepRepository.findById(talepId).orElseThrow().getDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
        assertThat(tekOlay().getDurum()).isEqualTo(GidenOlayDurumu.BEKLIYOR);
        assertThat(tekOlay().getDenemeSayisi()).isEqualTo(1);
        assertThat(tekOlay().getSonHata()).contains("gecici_engel");
    }

    @Test
    @DisplayName("Alicilardan biri patlarsa digerine yarim bildirim kalmaz, duzelince ikisi de birer kez alir")
    void yarimBildirimKalmaz() {
        onayaGonder("Klavye talebi");
        yazimiEngelle("bildirim", "alici_id <> " + ikinciAmir.getId());

        isleyici.bekleyenleriIsle();

        assertThat(bildirimRepository.count()).isZero();

        engelleriKaldir();
        beklemeSuresiniDoldur();
        isleyici.bekleyenleriIsle();

        assertThat(bildirimSayisi(amir)).isEqualTo(1);
        assertThat(bildirimSayisi(ikinciAmir)).isEqualTo(1);
    }

    @Test
    @DisplayName("Olay islendi isaretlenemezse yazilan bildirim de geri sarar")
    void isaretlenemeyenOlayBildirimBirakmaz() {
        onayaGonder("Klavye talebi");
        yazimiEngelle("giden_olay", "durum <> 'ISLENDI'");

        isleyici.bekleyenleriIsle();

        // Bildirim kalsaydi olay yeniden denendiginde ayni kisi ikinci kez bildirim alirdi.
        assertThat(bildirimRepository.count()).isZero();
        assertThat(tekOlay().getDurum()).isEqualTo(GidenOlayDurumu.BEKLIYOR);

        engelleriKaldir();
        beklemeSuresiniDoldur();
        isleyici.bekleyenleriIsle();

        assertThat(bildirimSayisi(amir)).isEqualTo(1);
    }

    @Test
    @DisplayName("Bekleme suresi dolmadan yeniden denenmez")
    void beklemeSuresiDolmadanDenenmez() {
        onayaGonder("Klavye talebi");
        yazimiEngelle("bildirim", "false");

        isleyici.bekleyenleriIsle();
        isleyici.bekleyenleriIsle();

        assertThat(tekOlay().getDenemeSayisi()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bozuk olay arkasindakileri tikamaz")
    void bozukOlayDigerleriniTikamaz() {
        Long bozukTalep = onayaGonder("Bozuk");
        Long saglamTalep = onayaGonder("Saglam");
        yazimiEngelle("bildirim", "talep_id <> " + bozukTalep);

        isleyici.bekleyenleriIsle();

        assertThat(gidenOlayRepository.findAll())
                .extracting(GidenOlay::getTalepId, GidenOlay::getDurum)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(bozukTalep, GidenOlayDurumu.BEKLIYOR),
                        org.assertj.core.groups.Tuple.tuple(saglamTalep, GidenOlayDurumu.ISLENDI));
        assertThat(bildirimSayisi(amir)).isEqualTo(1);
    }

    @Test
    @DisplayName("Deneme hakki biten olay basarisiz isaretlenir ve kendiliginden tekrar denenmez")
    void hakkiBitenOlayDenenmez() {
        onayaGonder("Klavye talebi");
        yazimiEngelle("bildirim", "false");

        // Ust sinir yalnizca sonsuz donguye karsi; asil durma kosulu olayin durumu.
        for (int tur = 0; tur < 50 && tekOlay().getDurum() == GidenOlayDurumu.BEKLIYOR; tur++) {
            beklemeSuresiniDoldur();
            isleyici.bekleyenleriIsle();
        }
        assertThat(tekOlay().getDurum()).isEqualTo(GidenOlayDurumu.BASARISIZ);

        engelleriKaldir();
        beklemeSuresiniDoldur();
        isleyici.bekleyenleriIsle();

        assertThat(bildirimRepository.count()).isZero();
    }

    @Test
    @DisplayName("Olayi baska bir islem tutuyorsa beklenmez, atlanir")
    void kilitliOlayAtlanir() {
        onayaGonder("Klavye talebi");
        Long olayId = tekOlay().getId();

        islem.executeWithoutResult(durum -> {
            assertThat(gidenOlayRepository.islemekIcinKilitle(olayId)).isPresent();

            // Beklemeye kalsaydi bu tur, kilidi tutan islem bitmeden donemezdi.
            CompletableFuture.runAsync(isleyici::bekleyenleriIsle).orTimeout(5, TimeUnit.SECONDS).join();
            assertThat(bildirimRepository.count()).isZero();
        });

        isleyici.bekleyenleriIsle();
        assertThat(bildirimSayisi(amir)).isEqualTo(1);
    }

    @Test
    @DisplayName("Iki isleyici ayni anda calisirsa her olay yine tek bildirim uretir")
    void esZamanliIsleyicilerTekBildirimUretir() {
        int olaySayisi = 20;
        for (int i = 0; i < olaySayisi; i++) {
            onayaGonder("Talep " + i);
        }

        CountDownLatch basla = new CountDownLatch(1);
        List<CompletableFuture<Void>> turlar = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            turlar.add(CompletableFuture.runAsync(() -> {
                try {
                    basla.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                isleyici.bekleyenleriIsle();
            }));
        }
        basla.countDown();
        CompletableFuture.allOf(turlar.toArray(CompletableFuture[]::new)).orTimeout(30, TimeUnit.SECONDS).join();

        assertThat(bildirimSayisi(amir)).isEqualTo(olaySayisi);
        assertThat(gidenOlayRepository.findAll())
                .extracting(GidenOlay::getDurum)
                .containsOnly(GidenOlayDurumu.ISLENDI);
    }

    // --- yardimcilar ----------------------------------------------------

    private Long onayaGonder(String baslik) {
        Long talepId = talepServisi.olustur(
                new TalepOlusturKomutu(baslik, "aciklama", TalepTuru.DONANIM, null), "islpersonel").id();
        talepServisi.onayaGonder(talepId, "islpersonel");
        return talepId;
    }

    private GidenOlay tekOlay() {
        List<GidenOlay> hepsi = gidenOlayRepository.findAll();
        assertThat(hepsi).hasSize(1);
        return hepsi.getFirst();
    }

    private long bildirimSayisi(Kullanici alici) {
        return bildirimRepository.countByAliciIdAndOkunduFalse(alici.getId());
    }

    private void beklemeSuresiniDoldur() {
        jdbc.update("UPDATE giden_olay SET sonraki_deneme = now() - interval '1 second'");
    }

    /** Neden mock degil kisit: bkz. GidenOlayServisiTest. */
    private void yazimiEngelle(String tablo, String izinKosulu) {
        jdbc.execute("ALTER TABLE " + tablo + " ADD CONSTRAINT gecici_engel CHECK (" + izinKosulu + ") NOT VALID");
    }

    private void engelleriKaldir() {
        jdbc.execute("ALTER TABLE bildirim DROP CONSTRAINT IF EXISTS gecici_engel");
        jdbc.execute("ALTER TABLE giden_olay DROP CONSTRAINT IF EXISTS gecici_engel");
    }
}
