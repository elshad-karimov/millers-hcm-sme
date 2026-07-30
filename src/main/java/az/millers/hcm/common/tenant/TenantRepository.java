package az.millers.hcm.common.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    List<Tenant> findByActiveTrue();

    Optional<Tenant> findByIssuerUri(String issuerUri);

    boolean existsByIssuerUri(String issuerUri);
}
