package tr.ebrar.talep.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Kabul kriteri: 1000 kayitta sayfali listeleme 200 ms altinda kalmali.
 *
 * <p>Olcum yontemi: 3 isinma turu (JIT derlemesi, baglanti havuzu, plan onbellegi),
 * ardindan 10 olcum turu. Karar medyana gore verilir; tek bir turun en kotu degeri
 * makinedeki baska bir isten etkilenebilir.
 */
@VeritabaniTesti
class TalepListelemePerformansTest extends VeritabaniTestTemeli {

    private static final Logger LOG = LoggerFactory.getLogger(TalepListelemePerformansTest.class);

    private static final int TALEP_SAYISI = 1000;
    private static final int ISINMA_TURU = 3;
    private static final int OLCUM_TURU = 10;
    private static final Duration UST_SINIR = Duration.ofMillis(200);

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private EntityManager entityManager;

    private Long birimId;

    @BeforeEach
    void hazirla() {
        Birim birim = VeriUretici.birim("PERF");
        entityManager.persist(birim);
        birimId = birim.getId();

        List<Kullanici> kullanicilar = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Kullanici kullanici = VeriUretici.kullanici("perf" + i, Rol.PERSONEL, birim);
            entityManager.persist(kullanici);
            kullanicilar.add(kullanici);
        }

        for (int i = 1; i <= TALEP_SAYISI; i++) {
            Kullanici sahip = kullanicilar.get(i % kullanicilar.size());
            Talep talep = VeriUretici.talep("Performans talebi " + i, sahip);
            if (i % 2 == 0) {
                talep.durumDegistir(TalepDurumu.BEKLEMEDE, sahip, null);
            }
            entityManager.persist(talep);

            // JDBC toplu yazimini kullanabilmek icin araliklarla bosaltiliyor;
            // aksi halde 1000 varlik tek seferde belekte birikip kurulumu yavaslatir.
            if (i % 100 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();

        // Planlayicinin gercekci bir plan secmesi icin istatistikler guncellenir.
        entityManager.createNativeQuery("ANALYZE talep").executeUpdate();
    }

    @Test
    @DisplayName("1000 kayitta 20'lik sayfa 200 ms altinda doner")
    void sayfaliListelemeHizli() {
        PageRequest sayfaIstegi = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "olusturmaTarihi"));

        for (int i = 0; i < ISINMA_TURU; i++) {
            calistir(sayfaIstegi);
        }

        List<Long> olcumler = new ArrayList<>();
        for (int i = 0; i < OLCUM_TURU; i++) {
            long baslangic = System.nanoTime();
            Page<Talep> sayfa = calistir(sayfaIstegi);
            olcumler.add((System.nanoTime() - baslangic) / 1_000_000);

            assertThat(sayfa.getContent()).hasSize(20);
            assertThat(sayfa.getTotalElements()).isEqualTo(TALEP_SAYISI / 2);
        }

        olcumler.sort(Long::compareTo);
        long medyan = olcumler.get(OLCUM_TURU / 2);
        long enKotu = olcumler.getLast();

        LOG.info("SAYFALAMA OLCUMU: {} kayit, sayfa boyutu 20, medyan={} ms, en kotu={} ms, tum olcumler={}",
                TALEP_SAYISI, medyan, enKotu, olcumler);

        assertThat(medyan)
                .as("1000 kayitta sayfali listeleme medyani ust sinirin altinda olmali")
                .isLessThan(UST_SINIR.toMillis());
    }

    private Page<Talep> calistir(PageRequest sayfaIstegi) {
        entityManager.clear();
        Page<Talep> sayfa = talepRepository.findByDurumAndBirimId(TalepDurumu.BEKLEMEDE, birimId, sayfaIstegi);
        // Iliskilere erisim de olcume dahil: N+1 varsa sure buradan patlar.
        sayfa.forEach(talep -> {
            talep.getTalepEden().getAdSoyad();
            talep.getBirim().getAd();
        });
        return sayfa;
    }
}
