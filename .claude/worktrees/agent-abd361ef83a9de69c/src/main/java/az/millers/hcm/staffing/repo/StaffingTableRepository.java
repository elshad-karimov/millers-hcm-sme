package az.millers.hcm.staffing.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.staffing.domain.StaffingTable;
import az.millers.hcm.staffing.domain.StaffingTableStatus;

public interface StaffingTableRepository extends JpaRepository<StaffingTable, UUID> {

    List<StaffingTable> findByLegalEntityIdOrderByEffectiveFromDesc(UUID legalEntityId);
    List<StaffingTable> findByLegalEntityIdAndStatusOrderByEffectiveFromDesc(UUID legalEntityId, StaffingTableStatus status);
    Optional<StaffingTable> findByLegalEntityIdAndVersionCode(UUID legalEntityId, String versionCode);

    /** Active staffing table for a legal entity at a given date, if any. */
    @Query("""
            select s from StaffingTable s
            where s.legalEntityId = :legalEntityId
              and s.status = az.millers.hcm.staffing.domain.StaffingTableStatus.ACTIVE
              and s.effectiveFrom <= :asOf
              and (s.effectiveTo is null or s.effectiveTo >= :asOf)
            order by s.effectiveFrom desc
            """)
    List<StaffingTable> findActiveAsOf(@Param("legalEntityId") UUID legalEntityId,
                                        @Param("asOf") LocalDate asOf);
}
