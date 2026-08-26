package tr.ebrar.talep.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;

import java.util.List;
import java.util.Optional;

public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    /**
     * Girise her istekte ihtiyac duyuldugu icin birim burada tek sorguda getiriliyor;
     * aksi halde kimlik dogrulama basina ikinci bir SELECT olusur.
     */
    @EntityGraph(attributePaths = "birim")
    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    boolean existsByKullaniciAdi(String kullaniciAdi);

    boolean existsByEposta(String eposta);

    /** Bir birimdeki belirli roldeki kullanicilar; bildirim aliciini bulmak icin. */
    List<Kullanici> findByBirimIdAndRolAndAktifTrue(Long birimId, Rol rol);

    @EntityGraph(attributePaths = "birim")
    Page<Kullanici> findAllBy(Pageable pageable);
}
