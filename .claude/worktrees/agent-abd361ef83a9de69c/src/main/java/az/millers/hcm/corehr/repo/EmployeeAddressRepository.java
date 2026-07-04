package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.AddressType;
import az.millers.hcm.corehr.domain.EmployeeAddress;

public interface EmployeeAddressRepository
        extends JpaRepository<EmployeeAddress, UUID> {

    List<EmployeeAddress> findByEmployeeIdOrderByAddressTypeAscEffectiveFromDesc(UUID employeeId);

    @Query("""
            select a from EmployeeAddress a
            where a.employeeId = :employeeId
              and a.addressType = :type
              and a.effectiveTo is null
            """)
    Optional<EmployeeAddress> findOpenForEmployee(UUID employeeId, AddressType type);

    /** Currently-open address of each type for an employee (zero or one per type). */
    @Query("""
            select a from EmployeeAddress a
            where a.employeeId = :employeeId
              and a.effectiveTo is null
            order by a.addressType
            """)
    List<EmployeeAddress> findOpenSlicesForEmployee(UUID employeeId);
}
