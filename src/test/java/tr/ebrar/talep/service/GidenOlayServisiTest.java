package tr.ebrar.talep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTemizleyici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.GidenOlayDurumu;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.GidenOlayRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.komut.Karar;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

/**
 * Olay kutusunun verdigi soz: durum degistiyse olay da yazilmistir, yazilmadiysa
 * durum da degismemistir.
 *
 * <p>Sinif {@code @Transactional} degil; gercek commit ve rollback gozleniyor
 * (bkz. TalepServisiTransactionTest). Zamanlayici test profilinde kapali.
 */
@SpringBootTest
class GidenOlayServisiTest extends VeritabaniTestTemeli {

    @Autowired
    private TalepServisi talepServisi;

    @Autowired
    private GidenOlayRepository gidenOlayRepository;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private OnayKaydiRepository onayKaydiRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private ApplicationEventPublisher olaylar;

    @Autowired
    private TransactionTemplate islem;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    private Kullanici personel;
    private Kullanici amir;
    private Birim birim;

    @BeforeEach
    void hazirla() {
        veritabaniTemizleyici.hepsiniTemizle();

        birim = birimRepository.save(VeriUretici.birim("GDO"));
        personel = kullaniciRepository.save(VeriUretici.kullanici("gdopersonel", Rol.PERSONEL, birim));
        amir = kullaniciRepository.save(VeriUretici.kullanici("gdoamir", Rol.AMIR, birim));
    }

    @AfterEach
    void temizle() {
        yazimEngeliniKaldir("giden_olay");
        veritabaniTemizleyici.hepsiniTemizle();
    }

    @Test
    @DisplayName("Durum degisince olay ayni anda kutuya yazilir")
    void durumDegisinceOlayYazilir() {
        Long talepId = onayaGonderilmisTalep("Klavye talebi");

        assertThat(gidenOlayRepository.findAll()).singleElement().satisfies(olay -> {
            assertThat(olay.getDurum()).isEqualTo(GidenOlayDurumu.BEKLIYOR);
            assertThat(olay.getTalepId()).isEqualTo(talepId);
            assertThat(olay.getTalepBasligi()).isEqualTo("Klavye talebi");
            assertThat(olay.getOncekiDurum()).isEqualTo(TalepDurumu.TASLAK);
            assertThat(olay.getYeniDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
            assertThat(olay.getIslemYapanId()).isEqualTo(personel.getId());
            assertThat(olay.getTalepSahibiId()).isEqualTo(personel.getId());
            assertThat(olay.getBirimId()).isEqualTo(birim.getId());
        });
    }

    @Test
    @DisplayName("Islem geri sararsa olay da geri sarar")
    void islemGeriSararsaOlayKalmaz() {
        Long talepId = onayaGonderilmisTalep("Klavye talebi");
        long oncekiOlaySayisi = gidenOlayRepository.count();

        // Karar metodu sorunsuz bitiyor, yani olay yazilmis oluyor; geri sarma ondan
        // SONRA geliyor. Olay kendi transaction'inda yazilsaydi burada geriye kalirdi.
        islem.executeWithoutResult(durum -> {
            talepServisi.karar(talepId, new OnayKarariKomutu(Karar.ONAYLA, "Uygundur"), "gdoamir");
            durum.setRollbackOnly();
        });

        assertThat(talepRepository.findById(talepId).orElseThrow().getDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
        assertThat(gidenOlayRepository.count()).isEqualTo(oncekiOlaySayisi);
    }

    @Test
    @DisplayName("Olay yazilamazsa durum da denetim kaydi da degismez")
    void olayYazilamazsaKararDaGeriSarar() {
        Long talepId = onayaGonderilmisTalep("Klavye talebi");
        long oncekiKayitSayisi = onayKaydiRepository.countByTalepId(talepId);
        yazimiEngelle("giden_olay", "false");

        assertThatThrownBy(() ->
                talepServisi.karar(talepId, new OnayKarariKomutu(Karar.ONAYLA, "Uygundur"), "gdoamir"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(talepRepository.findById(talepId).orElseThrow().getDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
        assertThat(onayKaydiRepository.countByTalepId(talepId)).isEqualTo(oncekiKayitSayisi);
    }

    @Test
    @DisplayName("Transaction disinda yayinlanan olay sessizce dusmez, hata verir")
    void transactionDisindaOlayKabulEdilmez() {
        assertThatThrownBy(() -> olaylar.publishEvent(new TalepDurumuDegistiOlayi(
                1L, "Baslik", TalepDurumu.TASLAK, TalepDurumu.BEKLEMEDE,
                personel.getId(), personel.getId(), birim.getId())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(gidenOlayRepository.count()).isZero();
    }

    // --- yardimcilar ----------------------------------------------------

    private Long onayaGonderilmisTalep(String baslik) {
        Long talepId = talepServisi.olustur(
                new TalepOlusturKomutu(baslik, "aciklama", TalepTuru.DONANIM, null), "gdopersonel").id();
        talepServisi.onayaGonder(talepId, "gdopersonel");
        return talepId;
    }

    /**
     * Yazimi veritabaninda patlatir. Mock yerine kisit: gercek bir kalicilik hatasi
     * transaction'i bozuyor, mock'un firlattigi istisna bozmuyor. NOT VALID oldugu
     * icin mevcut satirlara bakmiyor.
     */
    private void yazimiEngelle(String tablo, String izinKosulu) {
        jdbc.execute("ALTER TABLE " + tablo + " ADD CONSTRAINT gecici_engel CHECK (" + izinKosulu + ") NOT VALID");
    }

    private void yazimEngeliniKaldir(String tablo) {
        jdbc.execute("ALTER TABLE " + tablo + " DROP CONSTRAINT IF EXISTS gecici_engel");
    }
}
