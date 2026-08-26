package tr.ebrar.talep.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tr.ebrar.talep.domain.Birim;

import java.util.Optional;

public interface BirimRepository extends JpaRepository<Birim, Long> {

    Optional<Birim> findByKod(String kod);

    boolean existsByKod(String kod);
}
