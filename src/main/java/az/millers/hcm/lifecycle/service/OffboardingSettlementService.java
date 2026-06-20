package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementComponentRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementUpdateRequest;
import az.millers.hcm.lifecycle.domain.OffboardingSettlement;
import az.millers.hcm.lifecycle.domain.OffboardingSettlementComponent;
import az.millers.hcm.lifecycle.domain.SettlementStatus;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import az.millers.hcm.lifecycle.repo.OffboardingSettlementComponentRepository;
import az.millers.hcm.lifecycle.repo.OffboardingSettlementRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OffboardingSettlementService {

    private final OffboardingSettlementRepository settlementRepo;
    private final OffboardingSettlementComponentRepository componentRepo;
    private final OffboardingCaseRepository caseRepo;

    public Optional<OffboardingSettlement> findByCaseId(UUID caseId) {
        return settlementRepo.findByCaseId(caseId);
    }

    public List<OffboardingSettlementComponent> getComponents(UUID settlementId) {
        return componentRepo.findBySettlementIdOrderByIsDeductionAscCreatedAt(settlementId);
    }

    @Transactional
    public OffboardingSettlement getOrCreate(UUID caseId, String preparedBy) {
        return settlementRepo.findByCaseId(caseId).orElseGet(() -> {
            caseRepo.findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            OffboardingSettlement s = new OffboardingSettlement();
            s.setCaseId(caseId);
            s.setPreparedBy(preparedBy);
            return settlementRepo.save(s);
        });
    }

    @Transactional
    public OffboardingSettlement update(UUID settlementId, SettlementUpdateRequest req) {
        OffboardingSettlement s = settlementRepo.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
        if (req.paymentDate() != null) s.setPaymentDate(req.paymentDate());
        if (req.paymentMethod() != null) s.setPaymentMethod(req.paymentMethod());
        if (req.bankReference() != null) s.setBankReference(req.bankReference());
        if (req.notes() != null) s.setNotes(req.notes());
        if (req.preparedBy() != null) s.setPreparedBy(req.preparedBy());
        return settlementRepo.save(s);
    }

    @Transactional
    public OffboardingSettlementComponent addComponent(UUID settlementId, SettlementComponentRequest req) {
        OffboardingSettlement s = settlementRepo.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
        OffboardingSettlementComponent c = new OffboardingSettlementComponent();
        c.setSettlementId(settlementId);
        c.setComponentType(req.componentType());
        c.setDescription(req.description());
        c.setAmount(req.amount() != null ? req.amount() : BigDecimal.ZERO);
        c.setIsDeduction(Boolean.TRUE.equals(req.isDeduction()));
        c.setCalculationBasis(req.calculationBasis());
        OffboardingSettlementComponent saved = componentRepo.save(c);
        recalculate(s);
        return saved;
    }

    @Transactional
    public void deleteComponent(UUID componentId) {
        OffboardingSettlementComponent c = componentRepo.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
        UUID settlementId = c.getSettlementId();
        componentRepo.deleteById(componentId);
        settlementRepo.findById(settlementId).ifPresent(this::recalculate);
    }

    @Transactional
    public OffboardingSettlement transitionStatus(UUID settlementId, SettlementStatus status, String actor) {
        OffboardingSettlement s = settlementRepo.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
        s.setStatus(status);
        if (status == SettlementStatus.APPROVED) {
            s.setApprovedBy(actor);
            s.setApprovedAt(OffsetDateTime.now());
        }
        return settlementRepo.save(s);
    }

    private void recalculate(OffboardingSettlement s) {
        List<OffboardingSettlementComponent> comps = componentRepo
                .findBySettlementIdOrderByIsDeductionAscCreatedAt(s.getId());
        BigDecimal gross = comps.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeduction()))
                .map(OffboardingSettlementComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductions = comps.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsDeduction()))
                .map(OffboardingSettlementComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        s.setTotalGross(gross);
        s.setTotalDeductions(deductions);
        s.setNetPayable(gross.subtract(deductions));
        settlementRepo.save(s);
    }
}
