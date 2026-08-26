package tr.ebrar.talep.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import tr.ebrar.talep.domain.Bildirim;

public interface BildirimRepository extends JpaRepository<Bildirim, Long> {

    @EntityGraph(attributePaths = "talep")
    Page<Bildirim> findByAliciIdOrderByOlusturmaTarihiDesc(Long aliciId, Pageable pageable);

    long countByAliciIdAndOkunduFalse(Long aliciId);
}
