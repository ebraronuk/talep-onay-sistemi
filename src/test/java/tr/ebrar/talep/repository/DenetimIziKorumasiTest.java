package tr.ebrar.talep.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Denetim izinin gercekten degistirilemez oldugunu kanitlar.
 *
 * <p>Bu testler bir denetim bulgusunun karsiligi. Once koruma yalnizca Java
 * tarafindaydi: {@code OnayKaydi} sinifinda setter yok. Ama veritabanina baglanan
 * herhangi biri satiri guncelleyip silebiliyordu. Yani "denetim izi degistirilemez"
 * iddiasi kodun kendisi icin dogru, sistem icin yanlisti.
 *
 * <p>V5 migration'i ile trigger eklendi. Buradaki testler JPA'yi baypas edip
 * dogrudan SQL calistiriyor: korumanin uygulamada degil veritabaninda oldugunu
 * gostermenin tek yolu bu.
 */
@VeritabaniTesti
class DenetimIziKorumasiTest extends VeritabaniTestTemeli {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OnayKaydiRepository onayKaydiRepository;

    private Long talepId;

    @BeforeEach
    void hazirla() {
        Birim birim = VeriUretici.birim("DNT");
        entityManager.persist(birim);

        Kullanici personel = VeriUretici.kullanici("denetim1", Rol.PERSONEL, birim);
        entityManager.persist(personel);

        Talep talep = VeriUretici.talep("Denetim izi testi", personel);
        talep.durumDegistir(TalepDurumu.BEKLEMEDE, personel, "Onaya gonderildi");
        entityManager.persist(talep);

        entityManager.flush();
        talepId = talep.getId();
    }

    @Test
    @DisplayName("Denetim kaydi SQL ile bile guncellenemez")
    void guncellenemez() {
        assertThatThrownBy(() -> {
            entityManager
                    .createNativeQuery("UPDATE onay_kaydi SET aciklama = 'gecmisi degistirdim' WHERE talep_id = :id")
                    .setParameter("id", talepId)
                    .executeUpdate();
            entityManager.flush();
        })
                .hasMessageContaining("degistirilemez");
    }

    @Test
    @DisplayName("Denetim kaydi SQL ile bile silinemez")
    void silinemez() {
        assertThatThrownBy(() -> {
            entityManager
                    .createNativeQuery("DELETE FROM onay_kaydi WHERE talep_id = :id")
                    .setParameter("id", talepId)
                    .executeUpdate();
            entityManager.flush();
        })
                .hasMessageContaining("degistirilemez");
    }

    @Test
    @DisplayName("Yeni denetim kaydi eklenebiliyor: koruma yalnizca degisiklige karsi")
    void eklemeSerbest() {
        long oncesi = onayKaydiRepository.countByTalepId(talepId);

        Talep talep = entityManager.find(Talep.class, talepId);
        Kullanici personel = talep.getTalepEden();
        talep.durumDegistir(TalepDurumu.ONAYLANDI, personel, "Ek kayit");
        entityManager.flush();

        assertThat(onayKaydiRepository.countByTalepId(talepId)).isEqualTo(oncesi + 1);
    }
}
