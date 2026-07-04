package az.millers.hcm.compbenefits.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compbenefits.domain.BonusMatrixRule;

public interface BonusMatrixRuleRepository extends JpaRepository<BonusMatrixRule, UUID> {

    boolean existsByCode(String code);

    List<BonusMatrixRule> findAllByOrderByPriorityAscCodeAsc();

    @Query("""
           select r from BonusMatrixRule r
           where r.active = true
             and r.effectiveFrom <= :on
             and (r.effectiveTo is null or r.effectiveTo >= :on)
           order by r.priority asc, r.code asc
           """)
    List<BonusMatrixRule> findActiveOn(LocalDate on);
}
