package tr.ebrar.talep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import tr.ebrar.talep.destek.VeriUretici;
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
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.komut.Karar;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;

/**
 * Transaction sinirlarinin gercekten calistigini kanitlar.
 *
 * <p>Bu sinif bilerek {@code @Transactional} DEGIL. Test kendi transaction'ini
 * acsaydi servisin transaction'i ona katilir, rollback davranisi gozlemlenemezdi
 * (her sey zaten test sonunda geri sarilirdi). Bedeli: temizligi elle yapiyoruz.
 */
@SpringBootTest
class TalepServisiTransactionTest extends VeritabaniTestTemeli {

    @Autowired
    private TalepServisi talepServisi;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private OnayKaydiRepository onayKaydiRepository;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    @Autowired
    private BirimRepository birimRepository;

    @Autowired
    private BildirimRepository bildirimRepository;

    @Autowired
    private VeritabaniTemizleyici veritabaniTemizleyici;

    private Long talepId;

    @BeforeEach
    void hazirla() {
        temizle();

        Birim birim = birimRepository.save(VeriUretici.birim("TRX"));
        kullaniciRepository.save(VeriUretici.kullanici("trxpersonel", Rol.PERSONEL, birim));
        kullaniciRepository.save(VeriUretici.kullanici("trxamir", Rol.AMIR, birim));

        TalepDetayDto olusan = talepServisi.olustur(
                new TalepOlusturKomutu("Klavye talebi", "Mevcut klavye bozuldu", TalepTuru.DONANIM, null),
                "trxpersonel");
        talepId = olusan.id();

        talepServisi.onayaGonder(talepId, "trxpersonel");
    }

    @AfterEach
    void temizle() {
        veritabaniTemizleyici.hepsiniTemizle();
    }

    @Test
    @DisplayName("Onay kaydi yazilamazsa talebin durumu da degismez")
    void denetimKaydiYazilamazsaDurumGeriSarar() {
        long oncekiKayitSayisi = onayKaydiRepository.countByTalepId(talepId);
        assertThat(oncekiKayitSayisi).isEqualTo(2); // olusturma + onaya gonderme

        // onay_kaydi.aciklama kolonu VARCHAR(1000). 1500 karakter gonderince insert
        // veritabani seviyesinde patliyor. Servisi dogrudan cagirdigimiz icin
        // controller'daki @Valid devrede degil, yani hata tam da istedigimiz yerde,
        // denetim kaydi yazilirken cikiyor.
        String cokUzunAciklama = "x".repeat(1500);

        assertThatThrownBy(() -> talepServisi.karar(
                talepId, new OnayKarariKomutu(Karar.ONAYLA, cokUzunAciklama), "trxamir"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Asil iddia burada: durum degismemis olmali.
        assertThat(talepRepository.findById(talepId).orElseThrow().getDurum())
                .as("denetim kaydi yazilamadiysa durum da degismemeli")
                .isEqualTo(TalepDurumu.BEKLEMEDE);

        assertThat(onayKaydiRepository.countByTalepId(talepId))
                .as("yarim kalmis denetim kaydi olmamali")
                .isEqualTo(oncekiKayitSayisi);
    }

    @Test
    @DisplayName("Basarili onayda durum ve denetim kaydi birlikte yazilir")
    void basariliOnaydaIkisiBirlikteYazilir() {
        talepServisi.karar(talepId, new OnayKarariKomutu(Karar.ONAYLA, "Uygundur"), "trxamir");

        assertThat(talepRepository.findById(talepId).orElseThrow().getDurum())
                .isEqualTo(TalepDurumu.ONAYLANDI);
        assertThat(onayKaydiRepository.countByTalepId(talepId)).isEqualTo(3);
    }

    @Test
    @DisplayName("Es zamanli guncellemede ikinci yazim iyimser kilitten doner")
    void esZamanliGuncellemeIkincisiCakisir() {
        // Iki amirin ayni talebi ayni anda actigi senaryo. Gercek is parcaciklari
        // yerine iki ayri "oturum" simule ediliyor: burasi transactional olmayan
        // bir test oldugu icin findById kendi transaction'ini acip kapatiyor ve
        // elimizde ayrilmis (detached) bir kopya kaliyor.
        Talep birinciOturumunKopyasi = talepRepository.findById(talepId).orElseThrow();
        Long okunanSurum = birinciOturumunKopyasi.getSurum();

        // Ikinci oturum karari veriyor ve commit ediyor: surum ilerliyor.
        talepServisi.karar(talepId, new OnayKarariKomutu(Karar.ONAYLA, "Once ben"), "trxamir");

        assertThat(talepRepository.findById(talepId).orElseThrow().getSurum())
                .as("basarili yazim surumu ilerletmeli")
                .isGreaterThan(okunanSurum);

        // Birinci oturum elindeki eskimis kopyayi yazmaya calisiyor.
        birinciOturumunKopyasi.setBaslik("Eskimis oturumdan guncelleme");

        assertThatThrownBy(() -> talepRepository.saveAndFlush(birinciOturumunKopyasi))
                .as("@Version olmasaydi bu yazim sessizce kararin uzerine gecerdi")
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Kararin sonucu korunmus olmali.
        assertThat(talepRepository.findById(talepId).orElseThrow().getBaslik())
                .isEqualTo("Klavye talebi");
    }

    @Test
    @DisplayName("Commit sonrasi bildirim yaziliyor")
    void commitSonrasiBildirimYazilir() {
        // onaya gonderme adimi @BeforeEach icinde yapildi; amire bildirim dusmus olmali.
        Kullanici amir = kullaniciRepository.findByKullaniciAdi("trxamir").orElseThrow();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(bildirimRepository.countByAliciIdAndOkunduFalse(amir.getId())).isEqualTo(1));
    }

    @Test
    @DisplayName("Rollback olan islemde bildirim de yazilmaz")
    void rollbackOlanIslemdeBildirimYok() {
        Kullanici personel = kullaniciRepository.findByKullaniciAdi("trxpersonel").orElseThrow();
        long oncekiBildirim = bildirimRepository.countByAliciIdAndOkunduFalse(personel.getId());

        assertThatThrownBy(() -> talepServisi.karar(
                talepId, new OnayKarariKomutu(Karar.ONAYLA, "y".repeat(1500)), "trxamir"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Dinleyici AFTER_COMMIT'te. Commit olmadigi icin hic tetiklenmemeli.
        assertThat(bildirimRepository.countByAliciIdAndOkunduFalse(personel.getId()))
                .isEqualTo(oncekiBildirim);
    }
}
