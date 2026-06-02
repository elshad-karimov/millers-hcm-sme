package az.millers.hcm.letters.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.letters.domain.LetterTemplate;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, UUID> {
    Optional<LetterTemplate> findByCode(String code);
    List<LetterTemplate> findByActiveTrueOrderByNameAsc();
}
