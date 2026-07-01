package az.millers.hcm.compensation.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.compensation.domain.PayBand;

public interface PayBandRepository extends JpaRepository<PayBand, UUID> {

    List<PayBand> findByTenantIdAndIsActiveTrue(String tenantId);

    Optional<PayBand> findByTenantIdAndCode(String tenantId, String code);

    List<PayBand> findByGradeId(UUID gradeId);

    @Query("""
        SELECT pb FROM PayBand pb
        WHERE pb.tenantId = :tenantId
          AND pb.gradeId = :gradeId
          AND pb.isActive = true
          AND pb.effectiveFrom <= :date
          AND (pb.effectiveTo IS NULL OR pb.effectiveTo >= :date)
        ORDER BY pb.effectiveFrom DESC
        """)
    List<PayBand> findActiveOnForGrade(@Param("tenantId") String tenantId,
                                        @Param("gradeId") UUID gradeId,
                                        @Param("date") LocalDate date);
}
