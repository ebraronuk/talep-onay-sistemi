package tr.ebrar.talep.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Bildirim;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.OnayKaydi;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Her iliskinin gercekten LAZY oldugunu dogrular.
 *
 * <p>Bu testler "yanlislikla EAGER'a donmus mu" sorusunun bekcisi. Bir iliski
 * EAGER olursa liste ekranlari sessizce yavaslar ve bunu kimse fark etmez;
 * burada test kirmizi yanar.
 */
@VeritabaniTesti
class TembelYuklemeTest extends VeritabaniTestTemeli {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TalepRepository talepRepository;

    private Long talepId;
    private Long kullaniciId;
    private Long bildirimId;

    @BeforeEach
    void hazirla() {
        Birim birim = VeriUretici.birim("TMB");
        entityManager.persist(birim);

        Kullanici personel = VeriUretici.kullanici("tembel1", Rol.PERSONEL, birim);
        Kullanici amir = VeriUretici.kullanici("tembelamir", Rol.AMIR, birim);
        entityManager.persist(personel);
        entityManager.persist(amir);

        Talep talep = VeriUretici.talep("Tembel yukleme talebi", personel);
        talep.durumDegistir(TalepDurumu.BEKLEMEDE, personel, "Onaya gonderildi");
        entityManager.persist(talep);

        Bildirim bildirim = new Bildirim(amir, talep, "Yeni talep onayinizi bekliyor");
        entityManager.persist(bildirim);

        entityManager.flush();
        entityManager.clear();

        talepId = talep.getId();
        kullaniciId = personel.getId();
        bildirimId = bildirim.getId();
    }

    @Test
    @DisplayName("Talep.talepEden ve Talep.birim erisilene kadar yuklenmez")
    void talepIliskileriTembel() {
        Talep talep = talepRepository.findById(talepId).orElseThrow();

        assertThat(Hibernate.isInitialized(talep.getTalepEden())).as("talepEden baslangicta vekil").isFalse();
        assertThat(Hibernate.isInitialized(talep.getBirim())).as("birim baslangicta vekil").isFalse();

        talep.getTalepEden().getAdSoyad();

        assertThat(Hibernate.isInitialized(talep.getTalepEden())).isTrue();
        assertThat(Hibernate.isInitialized(talep.getBirim())).as("birime dokunulmadi, hala vekil").isFalse();
    }

    @Test
    @DisplayName("Talep.onayKayitlari koleksiyonu tembel")
    void onayKayitlariKoleksiyonuTembel() {
        Talep talep = talepRepository.findById(talepId).orElseThrow();

        // getOnayKayitlari() koleksiyonu unmodifiableList ile sardigi icin
        // Hibernate.isInitialized(...) buradan dogru cevap vermez: sarmalayici
        // artik PersistentCollection degil. Dogru arac alan adiyla sorgulayan
        // PersistenceUnitUtil.
        assertThat(koleksiyonYuklendiMi(talep)).as("koleksiyon baslangicta yuklenmemis").isFalse();

        assertThat(talep.getOnayKayitlari()).hasSize(1);

        assertThat(koleksiyonYuklendiMi(talep)).as("erisimden sonra yuklendi").isTrue();
    }

    private boolean koleksiyonYuklendiMi(Talep talep) {
        return entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(talep, "onayKayitlari");
    }

    @Test
    @DisplayName("Kullanici.birim tembel")
    void kullaniciBirimiTembel() {
        Kullanici kullanici = entityManager.find(Kullanici.class, kullaniciId);

        assertThat(Hibernate.isInitialized(kullanici.getBirim())).isFalse();
        assertThat(kullanici.getBirim().getKod()).isEqualTo("TMB");
        assertThat(Hibernate.isInitialized(kullanici.getBirim())).isTrue();
    }

    @Test
    @DisplayName("OnayKaydi.talep ve OnayKaydi.islemYapan tembel")
    void onayKaydiIliskileriTembel() {
        OnayKaydi kayit = entityManager.createQuery(
                        "select ok from OnayKaydi ok where ok.talep.id = :talepId", OnayKaydi.class)
                .setParameter("talepId", talepId)
                .getSingleResult();

        assertThat(Hibernate.isInitialized(kayit.getTalep())).isFalse();
        assertThat(Hibernate.isInitialized(kayit.getIslemYapan())).isFalse();
    }

    @Test
    @DisplayName("Bildirim.alici ve Bildirim.talep tembel")
    void bildirimIliskileriTembel() {
        Bildirim bildirim = entityManager.find(Bildirim.class, bildirimId);

        assertThat(Hibernate.isInitialized(bildirim.getAlici())).isFalse();
        assertThat(Hibernate.isInitialized(bildirim.getTalep())).isFalse();
    }

    @Test
    @DisplayName("Kalicilik baglami bosaltilinca tembel alana erisim LazyInitializationException firlatir")
    void ayrilmisVarliktaTembelErisimHataVerir() {
        Talep talep = talepRepository.findById(talepId).orElseThrow();
        Kullanici vekil = talep.getTalepEden();

        // Dikkat: yalnizca sahibi detach etmek yetmez. Vekil (proxy) nesnesi hala
        // acik oturuma bagli kalir ve kendini yukleyebilir. Hatanin cikmasi icin
        // vekilin oturumla bagi kopmali; clear() tum baglami ayirir.
        entityManager.clear();

        assertThatThrownBy(vekil::getAdSoyad)
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("Could not initialize proxy");
    }

    @Test
    @DisplayName("detayGetir tek sorguda tum gecmisi yuklu getirir")
    void detayGetirIliskileriYuklerGetirir() {
        Talep talep = talepRepository.detayGetir(talepId).orElseThrow();

        assertThat(Hibernate.isInitialized(talep.getTalepEden())).isTrue();
        assertThat(Hibernate.isInitialized(talep.getBirim())).isTrue();
        assertThat(Hibernate.isInitialized(talep.getOnayKayitlari())).isTrue();
        assertThat(talep.getOnayKayitlari()).hasSize(1);
    }
}
