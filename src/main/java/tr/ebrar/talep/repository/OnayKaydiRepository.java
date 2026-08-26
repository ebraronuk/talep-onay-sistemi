package tr.ebrar.talep.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tr.ebrar.talep.domain.OnayKaydi;

import java.util.List;

public interface OnayKaydiRepository extends JpaRepository<OnayKaydi, Long> {

    /** Talep detayindaki gecmis listesi; islem yapan kisi ile birlikte tek sorguda. */
    @EntityGraph(attributePaths = "islemYapan")
    List<OnayKaydi> findByTalepIdOrderByOlusturmaTarihiAsc(Long talepId);

    long countByTalepId(Long talepId);
}
