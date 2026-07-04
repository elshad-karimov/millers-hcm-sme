package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.DocumentType;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    List<DocumentType> findAllByOrderByOrdinalAscNameAsc();

    List<DocumentType> findByActiveTrueOrderByOrdinalAscNameAsc();

    List<DocumentType> findByActiveTrueAndDefaultRequiredTrueOrderByOrdinalAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}
