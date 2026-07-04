package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.CycleCalibrationTarget;
import az.millers.hcm.performance.repo.CycleCalibrationTargetRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;

/**
 * M121 — manages the per-cycle target distribution HR sets before
 * reviews close. Validation lives in pure-static
 * {@link CalibrationBoardMath#validateTargets} so it can be exercised
 * without Spring.
 */
@Service
public class CalibrationTargetService {

    private final CycleCalibrationTargetRepository targets;
    private final ReviewCycleRepository cycles;
    private final AuditService audit;

    public CalibrationTargetService(CycleCalibrationTargetRepository targets,
                                    ReviewCycleRepository cycles,
                                    AuditService audit) {
        this.targets = targets;
        this.cycles = cycles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> get(UUID cycleId) {
        cycles.findById(cycleId).orElseThrow(
                () -> new ResourceNotFoundException("Cycle not found: " + cycleId));
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String band : CalibrationBoardMath.CANONICAL_BANDS) out.put(band, null);
        for (CycleCalibrationTarget t : targets.findByCycleId(cycleId)) {
            out.put(t.getBand(), t.getTargetPercent());
        }
        return out;
    }

    @Transactional
    public Map<String, BigDecimal> save(UUID cycleId, Map<String, BigDecimal> requested) {
        cycles.findById(cycleId).orElseThrow(
                () -> new ResourceNotFoundException("Cycle not found: " + cycleId));
        // Strip nulls and zero-or-null entries so HR can clear a band by
        // passing null. validateTargets sees only the entries that
        // actually carry a target.
        Map<String, BigDecimal> effective = new LinkedHashMap<>();
        if (requested != null) {
            for (Map.Entry<String, BigDecimal> e : requested.entrySet()) {
                if (e.getValue() != null) effective.put(e.getKey(), e.getValue());
            }
        }
        if (effective.isEmpty()) {
            throw new BadRequestException("Provide at least one band target");
        }
        CalibrationBoardMath.validateTargets(effective);
        // Replace the row set.
        Map<String, BigDecimal> before = get(cycleId);
        targets.deleteByCycleId(cycleId);
        for (Map.Entry<String, BigDecimal> e : effective.entrySet()) {
            CycleCalibrationTarget t = new CycleCalibrationTarget();
            t.setCycleId(cycleId);
            t.setBand(e.getKey());
            t.setTargetPercent(e.getValue());
            targets.save(t);
        }
        audit.record("performance", "CycleCalibrationTarget", cycleId.toString(),
                "UPDATE", before, effective);
        return get(cycleId);
    }
}
