package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.compensation.domain.CompensationException;
import az.millers.hcm.compensation.domain.CompensationExceptionSource;
import az.millers.hcm.compensation.domain.CompensationExceptionStatus;
import az.millers.hcm.compensation.domain.CompensationExceptionType;

@Repository
public interface CompensationExceptionRepository extends JpaRepository<CompensationException, UUID> {

    List<CompensationException> findByTenantIdAndStatusOrderByRaisedAtDesc(String tenantId, CompensationExceptionStatus status);

    List<CompensationException> findByTenantIdAndExceptionTypeOrderByRaisedAtDesc(String tenantId, CompensationExceptionType type);

    List<CompensationException> findByTenantIdOrderByRaisedAtDesc(String tenantId);

    Optional<CompensationException> findBySourceTypeAndSourceIdAndExceptionType(
            CompensationExceptionSource sourceType, UUID sourceId, CompensationExceptionType exceptionType);
}
