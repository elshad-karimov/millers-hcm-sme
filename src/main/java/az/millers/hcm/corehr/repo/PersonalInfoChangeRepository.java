package az.millers.hcm.corehr.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.PersonalInfoChangeRequest;
import az.millers.hcm.corehr.domain.PersonalInfoChangeStatus;

public interface PersonalInfoChangeRepository
        extends JpaRepository<PersonalInfoChangeRequest, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('core_hr.personal_info_no_seq')", nativeQuery = true)
    long nextRequestNoSequence();

    List<PersonalInfoChangeRequest> findByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId);

    Page<PersonalInfoChangeRequest> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    Page<PersonalInfoChangeRequest> findByStatusOrderBySubmittedAtDesc(
            PersonalInfoChangeStatus status, Pageable pageable);

    Page<PersonalInfoChangeRequest> findByEmployeeIdInOrderBySubmittedAtDesc(
            Collection<UUID> employeeIds, Pageable pageable);

    Page<PersonalInfoChangeRequest> findByEmployeeIdInAndStatusOrderBySubmittedAtDesc(
            Collection<UUID> employeeIds, PersonalInfoChangeStatus status, Pageable pageable);
}
