package az.millers.hcm.corehr.repo;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    @Query(value = "SELECT nextval('core_hr.employee_no_seq')", nativeQuery = true)
    long nextEmployeeNoSequence();

    boolean existsByEmployeeNo(String employeeNo);

    /**
     * Loads {@code (id, national_id)} pairs for every row with a non-null
     * national ID. Used by {@code EmployeeService} to enforce
     * application-side uniqueness on the AES-encrypted national_id column
     * (M61 / P1-01).
     *
     * <p>A DB UNIQUE constraint can't catch duplicates because each row's
     * AES-GCM IV is independent — identical plaintexts produce different
     * ciphertexts. We scan + decrypt instead. Acceptable at HCM scale
     * (≤ ~10⁵ employees); revisit with a deterministic HMAC index if
     * tenants ever push past that.
     *
     * <p>Returns {@code Object[]} so we don't pull the full {@link Employee}
     * aggregate just to compare two columns. Hibernate decrypts the second
     * column via the {@code EncryptedStringConverter} as it would on a
     * normal load.
     */
    @Query("select e.id, e.nationalId from Employee e where e.nationalId is not null")
    java.util.List<Object[]> findIdAndNationalIdWhereNationalIdNotNull();

    java.util.Optional<Employee> findByEmployeeNo(String employeeNo);

    java.util.Optional<Employee> findByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<Employee> findByEmploymentStatus(EmploymentStatus status, Pageable pageable);

    Page<Employee> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
            String lastName, String firstName, Pageable pageable);

    /** Scope-bounded variants for ABAC-filtered listing (PRD 14.9). */
    Page<Employee> findByIdIn(Collection<UUID> ids, Pageable pageable);

    Page<Employee> findByIdInAndEmploymentStatus(
            Collection<UUID> ids, EmploymentStatus status, Pageable pageable);

    @Query("""
            select e from Employee e
            where e.id in :ids
              and (lower(e.lastName)  like lower(concat('%', :search, '%'))
                or lower(e.firstName) like lower(concat('%', :search, '%')))
            """)
    Page<Employee> findByIdInAndNameContaining(
            Collection<UUID> ids, String search, Pageable pageable);

    /**
     * Employee ids assigned to any of {@code orgUnitIds}. Used by
     * {@code AccessScopeService} to materialise an HR specialist's
     * org-unit scope (PRD 14.9). Returns an empty list when the set is
     * empty.
     */
    @Query("select e.id from Employee e where e.orgUnitId in :unitIds")
    java.util.List<UUID> findIdsByOrgUnitIdIn(java.util.Collection<UUID> unitIds);

    /**
     * All employee IDs in a given employment status. Used by the monthly
     * leave-accrual walker (PRD 8.5.2 — milestone 34) so it can iterate
     * without materialising the full {@link Employee} aggregate.
     */
    @Query("select e.id from Employee e where e.employmentStatus = :status")
    java.util.List<UUID> findIdsByEmploymentStatus(EmploymentStatus status);

    @Query("select e.id from Employee e where e.employmentStatus in :statuses")
    java.util.List<UUID> findIdsByEmploymentStatusIn(
            java.util.Collection<EmploymentStatus> statuses);

    /**
     * Returns {@code (id, hireDate)} pairs for the given employee IDs.
     * Used by {@link az.millers.hcm.leave.service.LeaveAccrualService}
     * to compute seniority-bracket tenure without loading the full
     * {@link az.millers.hcm.corehr.domain.Employee} aggregate (M47).
     */
    @Query("select e.id, e.hireDate from Employee e where e.id in :ids")
    java.util.List<Object[]> findIdAndHireDateByIdIn(
            @Param("ids") java.util.Collection<UUID> ids);

    /**
     * All employees whose status is not in the excluded set, ordered by name.
     * Used by the self-service peers endpoint so any authenticated user can
     * populate a replacement-employee picker without HR_ADMIN role.
     */
    @Query("select e from Employee e where e.employmentStatus not in :excluded order by e.lastName asc, e.firstName asc")
    java.util.List<Employee> findActiveColleagues(@Param("excluded") java.util.Collection<EmploymentStatus> excluded);

    /**
     * Returns the transitive set of employee IDs that report (directly or
     * indirectly) into {@code rootEmployeeId}, including the root itself.
     * Used by {@code AccessScopeService} to build the manager scope.
     *
     * <p>Uses a recursive CTE on {@code manager_id} — depth-bounded only by
     * the underlying tree, which is naturally shallow for HR data. The query
     * is in the {@code core_hr} schema explicitly so Hibernate's default
     * schema swap doesn't trip on it.
     */
    @Query(value = """
            WITH RECURSIVE chain (id) AS (
                SELECT id FROM core_hr.employee WHERE id = :root
                UNION ALL
                SELECT e.id FROM core_hr.employee e
                JOIN chain c ON e.manager_id = c.id
            )
            SELECT id FROM chain
            """,
            nativeQuery = true)
    java.util.List<UUID> descendantsIncluding(java.util.UUID root);
}
