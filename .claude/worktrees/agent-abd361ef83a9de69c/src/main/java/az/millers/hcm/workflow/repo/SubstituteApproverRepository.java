package az.millers.hcm.workflow.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.workflow.domain.SubstituteApprover;

public interface SubstituteApproverRepository extends JpaRepository<SubstituteApprover, UUID> {

    /** All active records where today is within the substitution window. */
    @Query("SELECT s FROM SubstituteApprover s WHERE s.startDate <= :today AND s.endDate >= :today")
    List<SubstituteApprover> findActive(@Param("today") LocalDate today);

    /**
     * Active substitutions where the given role is acting as substitute today.
     * Used in the inbox fan-out to find additional pending instances.
     */
    @Query("SELECT s FROM SubstituteApprover s WHERE s.substituteRole = :role " +
           "AND s.startDate <= :today AND s.endDate >= :today")
    List<SubstituteApprover> findActiveBySubstituteRole(
            @Param("role") String role, @Param("today") LocalDate today);

    /** Active substitutions for a given principal role. */
    @Query("SELECT s FROM SubstituteApprover s WHERE s.principalRole = :role " +
           "AND s.startDate <= :today AND s.endDate >= :today")
    List<SubstituteApprover> findActiveByPrincipalRole(
            @Param("role") String role, @Param("today") LocalDate today);
}
