package tr.ebrar.talep.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tr.ebrar.talep.domain.Birim;

public interface BirimRepository extends JpaRepository<Birim, Long> {

    boolean existsByKod(String kod);
}
