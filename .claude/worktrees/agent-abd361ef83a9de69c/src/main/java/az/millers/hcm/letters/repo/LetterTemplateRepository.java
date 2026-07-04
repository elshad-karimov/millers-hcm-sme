package az.millers.hcm.letters.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.letters.domain.LetterTemplate;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, UUID> {

    /**
     * M77 callers; resolves to the {@code en} variant when present, but
     * any matching code works since {@code (code, language)} is unique
     * — used by legacy paths that don't know about locale.
     */
    Optional<LetterTemplate> findByCode(String code);

    /** M139 — locale-aware lookup used by the renderer. */
    Optional<LetterTemplate> findByCodeAndLanguage(String code, String language);

    /** All variants for one code — admin uses to surface the language picker. */
    List<LetterTemplate> findByCodeOrderByLanguageAsc(String code);

    List<LetterTemplate> findByActiveTrueOrderByNameAsc();
}
