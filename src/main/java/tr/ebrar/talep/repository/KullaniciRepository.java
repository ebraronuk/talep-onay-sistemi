package tr.ebrar.talep.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;

public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    /**
     * Girise her istekte ihtiyac duyuldugu icin birim burada tek sorguda getiriliyor;
     * aksi halde kimlik dogrulama basina ikinci bir SELECT olusur.
     */
    @EntityGraph(attributePaths = "birim")
    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    /** Bir birimdeki belirli roldeki kullanicilar; bildirim alicisini bulmak icin. */
    List<Kullanici> findByBirimIdAndRolAndAktifTrue(Long birimId, Rol rol);

    /** Kurum genelindeki aktif kullanicilar; ikinci kademe onay bildirimi icin. */
    List<Kullanici> findByRolAndAktifTrue(Rol rol);
}
