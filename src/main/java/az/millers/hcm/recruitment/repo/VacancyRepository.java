package az.millers.hcm.recruitment.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    @Query(value = "SELECT nextval('recruitment.vacancy_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<Vacancy> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Vacancy> findByStatusOrderByCreatedAtDesc(VacancyStatus status, Pageable pageable);
}
