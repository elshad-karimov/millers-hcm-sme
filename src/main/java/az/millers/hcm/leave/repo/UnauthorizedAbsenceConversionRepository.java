package az.millers.hcm.leave.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.leave.domain.AbsenceConversionStatus;
import az.millers.hcm.leave.domain.UnauthorizedAbsenceConversion;

public interface UnauthorizedAbsenceConversionRepository
        extends JpaRepository<UnauthorizedAbsenceConversion, UUID> {

    Optional<UnauthorizedAbsenceConversion> findByEmployeeIdAndAbsenceDate(UUID employeeId, LocalDate date);

    @Query("""
        SELECT u FROM UnauthorizedAbsenceConversion u
        WHERE u.employeeId = :employeeId
          AND u.absenceDate BETWEEN :from AND :to
        ORDER BY u.absenceDate DESC
        """)
    List<UnauthorizedAbsenceConversion> findByEmployeeAndRange(
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT u FROM UnauthorizedAbsenceConversion u
        WHERE u.status = :status
        ORDER BY u.absenceDate DESC
        """)
    List<UnauthorizedAbsenceConversion> findByStatus(@Param("status") AbsenceConversionStatus status);

    @Query("""
        SELECT u FROM UnauthorizedAbsenceConversion u
        WHERE u.absenceDate BETWEEN :from AND :to
        ORDER BY u.absenceDate DESC, u.employeeId ASC
        """)
    List<UnauthorizedAbsenceConversion> findByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
