package tr.ebrar.talep.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.GidenOlay;
import tr.ebrar.talep.domain.GidenOlayDurumu;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

@VeritabaniTesti
class GidenOlayRepositoryTest extends VeritabaniTestTemeli {

    /** PostgreSQL mikrosaniye tutuyor; nanosaniye farki karsilastirmayi bozmasin. */
    private static final Instant SIMDI = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private static final Limit GENIS = Limit.of(100);

    @Autowired
    private GidenOlayRepository gidenOlayRepository;

    @Autowired
    private EntityManager entityManager;

    private Talep talep;

    @BeforeEach
    void hazirla() {
        Birim birim = VeriUretici.birim("GDN");
        entityManager.persist(birim);
        Kullanici personel = VeriUretici.kullanici("gdn.personel", Rol.PERSONEL, birim);
        entityManager.persist(personel);
        talep = VeriUretici.talep("Giden olay testi", personel);
        entityManager.persist(talep);
        entityManager.flush();
    }

    private GidenOlay kaydet(Instant denemeZamani) {
        return gidenOlayRepository.saveAndFlush(new GidenOlay(
                talep.getId(), talep.getBaslik(), TalepDurumu.TASLAK, TalepDurumu.BEKLEMEDE,
                talep.getTalepEden().getId(), talep.getTalepEden().getId(), talep.getBirim().getId(),
                denemeZamani));
    }

    @Test
    @DisplayName("Yalnizca zamani gelmis bekleyen olaylar, eskiden yeniye doner")
    void zamaniGelmisBekleyenlerDoner() {
        GidenOlay ilk = kaydet(SIMDI.minusSeconds(1));
        GidenOlay tamZamaninda = kaydet(SIMDI);
        kaydet(SIMDI.plusSeconds(60));

        GidenOlay islenmis = kaydet(SIMDI.minusSeconds(1));
        islenmis.islendi(SIMDI);

        GidenOlay vazgecilmis = kaydet(SIMDI.minusSeconds(1));
        while (vazgecilmis.getDurum() == GidenOlayDurumu.BEKLIYOR) {
            vazgecilmis.denemeBasarisiz("hata", SIMDI.minusSeconds(3600));
        }
        gidenOlayRepository.flush();

        assertThat(gidenOlayRepository.islenecekKimlikler(SIMDI, GENIS))
                .containsExactly(ilk.getId(), tamZamaninda.getId());
    }

    @Test
    @DisplayName("Tek turda alinan olay sayisi sinirlanir")
    void partiSiniriUygulanir() {
        GidenOlay ilk = kaydet(SIMDI);
        GidenOlay ikinci = kaydet(SIMDI);
        kaydet(SIMDI);

        assertThat(gidenOlayRepository.islenecekKimlikler(SIMDI, Limit.of(2)))
                .containsExactly(ilk.getId(), ikinci.getId());
    }

    @Test
    @DisplayName("Islenmek uzere yalnizca bekleyen olay kilitlenir")
    void yalnizcaBekleyenKilitlenir() {
        GidenOlay bekleyen = kaydet(SIMDI);
        GidenOlay islenmis = kaydet(SIMDI);
        islenmis.islendi(SIMDI);
        gidenOlayRepository.flush();

        assertThat(gidenOlayRepository.islemekIcinKilitle(bekleyen.getId())).contains(bekleyen);
        assertThat(gidenOlayRepository.islemekIcinKilitle(islenmis.getId())).isEmpty();
        assertThat(gidenOlayRepository.islemekIcinKilitle(-1L)).isEmpty();
    }

    @Test
    @DisplayName("Islendi denen satirin islenme tarihi olmak zorunda, kural veritabaninda")
    void islendiTarihsizOlamaz() {
        GidenOlay olay = kaydet(SIMDI);

        assertThatThrownBy(() -> entityManager
                .createNativeQuery("UPDATE giden_olay SET durum = 'ISLENDI' WHERE id = " + olay.getId())
                .executeUpdate())
                .hasMessageContaining("ck_giden_olay_islenme");
    }
}
