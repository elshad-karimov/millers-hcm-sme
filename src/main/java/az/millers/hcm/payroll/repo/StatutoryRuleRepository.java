package az.millers.hcm.payroll.repo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.StatutoryRule;

public interface StatutoryRuleRepository extends JpaRepository<StatutoryRule, UUID> {

    @Query("""
            select r from StatutoryRule r
            where r.code = :code
              and r.jurisdiction = :jurisdiction
              and r.active = true
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo >= :date)
            order by r.effectiveFrom desc
            """)
    Optional<StatutoryRule> findActive(String code, String jurisdiction, LocalDate date);
}
