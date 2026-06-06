package az.millers.hcm.skills.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.skills.api.SkillsDtos.PositionCompetencyRequest;
import az.millers.hcm.skills.api.SkillsDtos.PositionCompetencyResponse;
import az.millers.hcm.staffing.domain.PositionRequiredCompetency;
import az.millers.hcm.staffing.repo.PositionRequiredCompetencyRepository;

/**
 * M127 — CRUD on per-position competency requirements. Audited; every
 * add/update/remove writes to {@code audit_log}.
 */
@Service
public class PositionRequirementsService {

    private final PositionRequiredCompetencyRepository repo;
    private final CompetencyRepository competencies;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionRequirementsService(PositionRequiredCompetencyRepository repo,
                                       CompetencyRepository competencies,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.repo = repo;
        this.competencies = competencies;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PositionCompetencyResponse> listFor(UUID positionId) {
        List<PositionRequiredCompetency> rows = repo
                .findByPositionIdOrderByMandatoryDescRequiredLevelDesc(positionId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Competency> byId = loadCompetencies(rows);
        List<PositionCompetencyResponse> out = new java.util.ArrayList<>(rows.size());
        for (PositionRequiredCompetency r : rows) out.add(toResponse(r, byId.get(r.getCompetencyId())));
        return out;
    }

    @Transactional
    public PositionCompetencyResponse upsert(UUID positionId, PositionCompetencyRequest req) {
        if (req.competencyId() == null) {
            throw new BadRequestException("competencyId is required");
        }
        if (req.requiredLevel() < 1 || req.requiredLevel() > 5) {
            throw new BadRequestException("requiredLevel must be 1..5");
        }
        Competency comp = competencies.findById(req.competencyId())
                .orElseThrow(() -> new BadRequestException("Unknown competencyId"));
        PositionRequiredCompetency row = repo
                .findByPositionIdAndCompetencyId(positionId, req.competencyId())
                .orElseGet(() -> {
                    PositionRequiredCompetency fresh = new PositionRequiredCompetency();
                    fresh.setPositionId(positionId);
                    fresh.setCompetencyId(req.competencyId());
                    fresh.setCreatedBy(currentRequest.username());
                    return fresh;
                });
        boolean isNew = row.getId() == null;
        Map<String, Object> before = isNew ? null : snapshot(row);
        row.setRequiredLevel(req.requiredLevel());
        row.setMandatory(req.mandatory() == null ? Boolean.TRUE : req.mandatory());
        row.setNotes(req.notes());
        row.setUpdatedBy(currentRequest.username());
        PositionRequiredCompetency saved = repo.save(row);
        audit.record("staffing", "PositionRequiredCompetency", saved.getId().toString(),
                isNew ? "CREATE" : "UPDATE", before, snapshot(saved));
        return toResponse(saved, comp);
    }

    @Transactional
    public void remove(UUID positionId, UUID competencyId) {
        PositionRequiredCompetency row = repo
                .findByPositionIdAndCompetencyId(positionId, competencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));
        Map<String, Object> before = snapshot(row);
        repo.delete(row);
        audit.record("staffing", "PositionRequiredCompetency", row.getId().toString(),
                "DELETE", before, null);
    }

    private Map<UUID, Competency> loadCompetencies(List<PositionRequiredCompetency> rows) {
        List<UUID> ids = rows.stream().map(PositionRequiredCompetency::getCompetencyId).distinct().toList();
        Map<UUID, Competency> out = new HashMap<>();
        for (Competency c : competencies.findAllById(ids)) out.put(c.getId(), c);
        return out;
    }

    private static PositionCompetencyResponse toResponse(PositionRequiredCompetency r, Competency c) {
        return new PositionCompetencyResponse(
                r.getId(), r.getPositionId(), r.getCompetencyId(),
                c == null ? null : c.getCode(),
                c == null ? null : c.getName(),
                c == null ? null : (c.getCategory() == null ? null : c.getCategory().name()),
                r.getRequiredLevel(), r.isMandatory(), r.getNotes(),
                r.getCreatedAt(), r.getCreatedBy(),
                r.getUpdatedAt(), r.getUpdatedBy());
    }

    private static Map<String, Object> snapshot(PositionRequiredCompetency r) {
        Map<String, Object> m = new HashMap<>();
        m.put("competencyId", r.getCompetencyId());
        m.put("requiredLevel", r.getRequiredLevel());
        m.put("mandatory", r.isMandatory());
        m.put("notes", r.getNotes());
        return m;
    }

    /** Last-touched timestamp — exposed so the audit log lines up neatly. */
    @SuppressWarnings("unused")
    private static OffsetDateTime now() { return OffsetDateTime.now(); }
}
