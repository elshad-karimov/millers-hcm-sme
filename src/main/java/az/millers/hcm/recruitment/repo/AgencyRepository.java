package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Agency;

public interface AgencyRepository extends JpaRepository<Agency, UUID> {

    @Query(value = "SELECT nextval('recruitment.agency_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Agency> findAllByOrderByNameAsc();

    List<Agency> findByActiveTrueOrderByNameAsc();
}
