package tr.ebrar.talep.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import tr.ebrar.talep.destek.SorguSayaci;
import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

/**
 * N+1 sorgu probleminin kaniti ve cozumu.
 *
 * <p>Kurulum bilincli olarak N+1'i tetikleyecek sekilde yapildi: 10 talebin her biri
 * <b>farkli</b> bir kullaniciya ait. Hepsi ayni kullaniciya ait olsaydi Hibernate
 * ilk yuklemeden sonra kalicilik baglamindan (persistence context) donerdi ve
 * problem gorunmezdi. Gercek hayatta liste ekranlari zaten farkli kisilerin
 * taleplerini gosterir.
 *
 * <p>SQL loglari acik; testi calistirip {@code select ... from kullanici} satirlarini
 * saymak, buradaki sayilari gozle dogrulamanin en hizli yolu.
 */
@VeritabaniTesti
@TestPropertySource(properties = {
        "spring.jpa.show-sql=true",
        "logging.level.org.hibernate.SQL=DEBUG"
})
class TalepNArtiBirTest extends VeritabaniTestTemeli {

    private static final Logger LOG = LoggerFactory.getLogger(TalepNArtiBirTest.class);
    private static final int TALEP_SAYISI = 10;

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private EntityManager entityManager;

    private SorguSayaci sayac;

    @BeforeEach
    void hazirla() {
        Birim birim = new Birim("NB1", "N arti bir test birimi");
        entityManager.persist(birim);

        for (int i = 1; i <= TALEP_SAYISI; i++) {
            Kullanici kullanici = VeriUretici.kullanici("narti" + i, Rol.PERSONEL, birim);
            entityManager.persist(kullanici);
            Talep talep = VeriUretici.talep("N+1 test talebi " + i, kullanici);
            entityManager.persist(talep);
        }

        // Kalicilik baglami temizlenmezse varliklar zaten bellekte olur ve
        // hicbir SELECT calismaz; olcum anlamsizlasir.
        entityManager.flush();
        entityManager.clear();

        sayac = SorguSayaci.ac(entityManager);
    }

    @Test
    @DisplayName("ONCESI: iliski grafigi olmadan 10 talep icin 11 sorgu calisir (N+1)")
    void grafiksizListelemeNArtiBirUretir() {
        sayac.sifirla();

        List<Talep> talepler = talepRepository.findAll();
        long listeSorgusu = sayac.sayi();

        talepler.forEach(talep -> talep.getTalepEden().getAdSoyad());
        long toplam = sayac.sayi();

        LOG.info("N+1 OLCUMU (grafiksiz): liste sorgusu={}, iliskiler icin ek sorgu={}, toplam={}",
                listeSorgusu, toplam - listeSorgusu, toplam);

        assertThat(talepler).hasSize(TALEP_SAYISI);
        assertThat(listeSorgusu).as("liste sorgusu tek deyim").isEqualTo(1);
        assertThat(toplam - listeSorgusu)
                .as("her talep icin ayri bir kullanici sorgusu")
                .isEqualTo(TALEP_SAYISI);
        assertThat(toplam).as("klasik N+1: 1 + N").isEqualTo(TALEP_SAYISI + 1L);
    }

    @Test
    @DisplayName("SONRASI: @EntityGraph ile ayni liste tek sorguya duser")
    void entityGraphIleTekSorgu() {
        sayac.sifirla();

        List<Talep> talepler = talepRepository.findByDurumOrderByIdAsc(TalepDurumu.TASLAK);
        long listeSorgusu = sayac.sayi();

        talepler.forEach(talep -> {
            talep.getTalepEden().getAdSoyad();
            talep.getBirim().getAd();
        });
        long toplam = sayac.sayi();

        LOG.info("N+1 OLCUMU (@EntityGraph): liste sorgusu={}, iliskiler icin ek sorgu={}, toplam={}",
                listeSorgusu, toplam - listeSorgusu, toplam);

        assertThat(talepler).hasSize(TALEP_SAYISI);
        assertThat(listeSorgusu).as("iliskiler join ile ayni sorguda geldi").isEqualTo(1);
        assertThat(toplam - listeSorgusu)
                .as("iliskilere erisim ek sorgu uretmemeli")
                .isZero();
    }

    @Test
    @DisplayName("Specification ile fetch join da tek sorgu uretir, sayfalama bozulmaz")
    void specificationFetchJoinIleTekSorgu() {
        sayac.sifirla();

        var kriter = org.springframework.data.jpa.domain.Specification
                .allOf(TalepSpecifications.durumu(TalepDurumu.TASLAK), TalepSpecifications.iliskileriGetir());

        var sayfa = talepRepository.findAll(kriter, org.springframework.data.domain.PageRequest.of(0, 5));
        long yuklemeSorgusu = sayac.sayi();

        sayfa.forEach(talep -> {
            talep.getTalepEden().getAdSoyad();
            talep.getBirim().getAd();
        });
        long toplam = sayac.sayi();

        LOG.info("N+1 OLCUMU (Specification + fetch): yukleme={} (icerik + count), ek sorgu={}",
                yuklemeSorgusu, toplam - yuklemeSorgusu);

        assertThat(sayfa.getContent()).hasSize(5);
        assertThat(sayfa.getTotalElements()).isEqualTo(TALEP_SAYISI);
        assertThat(yuklemeSorgusu).as("icerik sorgusu + toplam adet sorgusu").isEqualTo(2);
        assertThat(toplam - yuklemeSorgusu).as("iliskilere erisim ek sorgu uretmemeli").isZero();
    }
}
