package tr.ebrar.talep.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;
import tr.ebrar.talep.destek.VeritabaniTesti;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

@VeritabaniTesti
class TalepRepositoryTest extends VeritabaniTestTemeli {

    @Autowired
    private TalepRepository talepRepository;

    @Autowired
    private EntityManager entityManager;

    private Birim btgm;
    private Birim muhasebe;
    private Kullanici personel;
    private Kullanici amir;

    @BeforeEach
    void hazirla() {
        btgm = VeriUretici.birim("RPO-BT");
        muhasebe = VeriUretici.birim("RPO-MH");
        entityManager.persist(btgm);
        entityManager.persist(muhasebe);

        personel = VeriUretici.kullanici("repopersonel", Rol.PERSONEL, btgm);
        amir = VeriUretici.kullanici("repoamir", Rol.AMIR, btgm);
        Kullanici muhasebeci = VeriUretici.kullanici("repomuhasebe", Rol.PERSONEL, muhasebe);
        entityManager.persist(personel);
        entityManager.persist(amir);
        entityManager.persist(muhasebeci);

        // BTGM: 3 beklemede, 1 taslak. Muhasebe: 1 beklemede.
        for (int i = 1; i <= 3; i++) {
            Talep talep = VeriUretici.talep("BTGM bekleyen " + i, personel);
            talep.durumDegistir(TalepDurumu.BEKLEMEDE, personel, null);
            entityManager.persist(talep);
        }
        entityManager.persist(VeriUretici.talep("BTGM taslak", personel));

        Talep muhasebeTalebi = VeriUretici.talep("Muhasebe bekleyen", muhasebeci);
        muhasebeTalebi.durumDegistir(TalepDurumu.BEKLEMEDE, muhasebeci, null);
        entityManager.persist(muhasebeTalebi);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Birim ve duruma gore sayfali listeleme yalnizca o birimin taleplerini dondurur")
    void birimeGoreListeleme() {
        Page<Talep> sayfa = talepRepository.findByDurumAndBirimId(
                TalepDurumu.BEKLEMEDE, btgm.getId(), PageRequest.of(0, 10));

        assertThat(sayfa.getTotalElements()).isEqualTo(3);
        assertThat(sayfa.getContent()).allSatisfy(talep ->
                assertThat(talep.getBirim().getKod()).isEqualTo("RPO-BT"));
    }

    @Test
    @DisplayName("Sayfalama sayfa boyutuna ve toplam sayiya uyar")
    void sayfalamaCalisir() {
        Page<Talep> ilkSayfa = talepRepository.findByTalepEdenId(
                personel.getId(), PageRequest.of(0, 2, Sort.by("id")));

        assertThat(ilkSayfa.getContent()).hasSize(2);
        assertThat(ilkSayfa.getTotalElements()).isEqualTo(4);
        assertThat(ilkSayfa.getTotalPages()).isEqualTo(2);
        assertThat(ilkSayfa.hasNext()).isTrue();

        Page<Talep> ikinciSayfa = talepRepository.findByTalepEdenId(
                personel.getId(), PageRequest.of(1, 2, Sort.by("id")));

        assertThat(ikinciSayfa.getContent()).hasSize(2);
        assertThat(ikinciSayfa.hasNext()).isFalse();
        assertThat(ikinciSayfa.getContent()).doesNotContainAnyElementsOf(ilkSayfa.getContent());
    }

    @Test
    @DisplayName("Specification'lar birlestirilerek cok kriterli filtreleme yapilir")
    void specificationIleFiltreleme() {
        Specification<Talep> kriter = Specification.allOf(
                TalepSpecifications.durumu(TalepDurumu.BEKLEMEDE),
                TalepSpecifications.birimi(btgm.getId()),
                TalepSpecifications.baslikIcerir("bekleyen"),
                TalepSpecifications.turu(TalepTuru.DIGER));

        List<Talep> sonuc = talepRepository.findAll(kriter);

        assertThat(sonuc).hasSize(3);
    }

    @Test
    @DisplayName("Bos filtreler sonucu daraltmaz")
    void bosSpecificationTumunuDondurur() {
        Specification<Talep> kriter = Specification.allOf(
                TalepSpecifications.durumu(null),
                TalepSpecifications.birimi(null),
                TalepSpecifications.baslikIcerir("   "));

        assertThat(talepRepository.findAll(kriter)).hasSize(5);
    }

    @Test
    @DisplayName("Durum ozeti gruplamayi veritabaninda yapar")
    void durumOzetiGetirir() {
        List<DurumOzeti> ozet = talepRepository.durumOzetiGetir(btgm.getId());

        assertThat(ozet).containsExactlyInAnyOrder(
                new DurumOzeti(TalepDurumu.TASLAK, 1),
                new DurumOzeti(TalepDurumu.BEKLEMEDE, 3));
    }

    @Test
    @DisplayName("Durum ozeti birim verilmediginde tum sistemi kapsar")
    void durumOzetiBirimsizTumunuKapsar() {
        List<DurumOzeti> ozet = talepRepository.durumOzetiGetir(null);

        assertThat(ozet).containsExactlyInAnyOrder(
                new DurumOzeti(TalepDurumu.TASLAK, 1),
                new DurumOzeti(TalepDurumu.BEKLEMEDE, 4));
    }

    @Test
    @DisplayName("Denetim alanlari kayit sirasinda otomatik dolar")
    void denetimAlanlariDolar() {
        Talep talep = talepRepository.findAll().getFirst();

        assertThat(talep.getOlusturmaTarihi()).isNotNull();
        assertThat(talep.getGuncellemeTarihi()).isNotNull();
        assertThat(talep.getOlusturanKullanici()).isEqualTo("sistem");
    }

    @Test
    @DisplayName("Durum degisikligi onay kaydini ayni islemde yazar")
    void durumDegisikligiOnayKaydiYazar() {
        Talep talep = talepRepository.findByDurumAndBirimId(
                TalepDurumu.BEKLEMEDE, btgm.getId(), PageRequest.of(0, 1)).getContent().getFirst();

        talep.durumDegistir(TalepDurumu.ONAYLANDI, amir, "Uygundur");
        talepRepository.saveAndFlush(talep);
        entityManager.clear();

        Talep yeniden = talepRepository.detayGetir(talep.getId()).orElseThrow();

        assertThat(yeniden.getDurum()).isEqualTo(TalepDurumu.ONAYLANDI);
        // 3 kayit: olusturma, onaya gonderme, onaylama
        assertThat(yeniden.getOnayKayitlari()).hasSize(3);
        assertThat(yeniden.getOnayKayitlari().getLast().getYeniDurum()).isEqualTo(TalepDurumu.ONAYLANDI);
        assertThat(yeniden.getOnayKayitlari().getLast().getAciklama()).isEqualTo("Uygundur");
    }

    @Test
    @DisplayName("countByDurumAndBirimId veriyi belege cekmeden sayar")
    void sayimCalisir() {
        assertThat(talepRepository.countByDurumAndBirimId(TalepDurumu.BEKLEMEDE, btgm.getId())).isEqualTo(3);
        assertThat(talepRepository.countByDurumAndBirimId(TalepDurumu.ONAYLANDI, btgm.getId())).isZero();
    }
}
