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

    /**
     * Sum of {@code openings} across vacancies in the given status — used by
     * {@code PositionHeadcountService} to count outstanding requisitions
     * against a position's approved headcount (M109).
     */
    @Query("select coalesce(sum(v.openings), 0) from Vacancy v "
            + "where v.positionId = :positionId and v.status = :status")
    int sumOpeningsByPositionAndStatus(@org.springframework.data.repository.query.Param("positionId") UUID positionId,
                                       @org.springframework.data.repository.query.Param("status") VacancyStatus status);
}
