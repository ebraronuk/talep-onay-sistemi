package tr.ebrar.talep.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import tr.ebrar.talep.domain.OnayKaydi;

public interface OnayKaydiRepository extends JpaRepository<OnayKaydi, Long> {

    /** Talep detayindaki gecmis listesi; islem yapan kisi ile birlikte tek sorguda. */
    @EntityGraph(attributePaths = "islemYapan")
    List<OnayKaydi> findByTalepIdOrderByOlusturmaTarihiAsc(Long talepId);

    long countByTalepId(Long talepId);
}
