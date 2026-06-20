package az.millers.hcm.lifecycle.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.lifecycle.domain.OffboardingSettlement;
import az.millers.hcm.lifecycle.domain.SettlementStatus;

public interface OffboardingSettlementRepository extends JpaRepository<OffboardingSettlement, UUID> {
    Optional<OffboardingSettlement> findByCaseId(UUID caseId);

    @Query("SELECT COALESCE(SUM(s.totalGross), 0) FROM OffboardingSettlement s WHERE s.status = :status AND s.paymentDate BETWEEN :from AND :to")
    BigDecimal sumGrossByStatusAndPaymentDateBetween(@Param("status") SettlementStatus status,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(s.netPayable), 0) FROM OffboardingSettlement s WHERE s.status = :status AND s.paymentDate BETWEEN :from AND :to")
    BigDecimal sumNetByStatusAndPaymentDateBetween(@Param("status") SettlementStatus status,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
