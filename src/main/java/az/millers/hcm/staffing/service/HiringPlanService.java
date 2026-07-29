package az.millers.hcm.staffing.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.staffing.domain.HiringPlanLine;
import az.millers.hcm.staffing.domain.HiringPlanLine.RecruitmentStatus;
import az.millers.hcm.staffing.domain.WorkforcePlan;
import az.millers.hcm.staffing.domain.WorkforcePlanLine;
import az.millers.hcm.staffing.repo.HiringPlanLineRepository;
import az.millers.hcm.staffing.repo.WorkforcePlanLineRepository;
import az.millers.hcm.staffing.repo.WorkforcePlanRepository;

/**
 * M422: Hiring plan integration — generate hiring plan from approved workforce plan.
 */
@Service
public class HiringPlanService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "HiringPlan";

    private final HiringPlanLineRepository hiringPlanLines;
    private final WorkforcePlanRepository workforcePlans;
    private final WorkforcePlanLineRepository planLines;
    private final AuditService audit;

    public HiringPlanService(HiringPlanLineRepository hiringPlanLines,
                              WorkforcePlanRepository workforcePlans,
                              WorkforcePlanLineRepository planLines,
                              AuditService audit) {
        this.hiringPlanLines = hiringPlanLines;
        this.workforcePlans = workforcePlans;
        this.planLines = planLines;
        this.audit = audit;
    }

    @Transactional
    public List<HiringPlanLine> generateFromPlan(UUID workforcePlanId) {
        WorkforcePlan plan = workforcePlans.findById(workforcePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Workforce plan not found"));

        // Only generate from approved plans
        if (!"APPROVED".equals(plan.getStatus().name())) {
            throw new BadRequestException("Workforce plan must be APPROVED to generate hiring plan");
        }

        // Find NEW_HIRE plan lines
        List<WorkforcePlanLine> newHires = planLines.findByWorkforcePlanIdOrderByLineNoAsc(workforcePlanId)
                .stream()
                .filter(line -> "NEW_HIRE".equalsIgnoreCase(line.getChangeType()))
                .toList();

        if (newHires.isEmpty()) {
            throw new BadRequestException("No NEW_HIRE lines found in workforce plan");
        }

        // Generate hiring plan lines
        List<HiringPlanLine> lines = newHires.stream()
                .map(wl -> {
                    HiringPlanLine hl = new HiringPlanLine();
                    hl.setTenantId(TenantContext.current());
                    hl.setWorkforcePlanId(workforcePlanId);
                    // Note: position_id would need to be in WorkforcePlanLine if we want to link
                    hl.setTargetStartDate(wl.getTargetStartDate());
                    hl.setHeadcount(wl.getPlannedHeadcount());
                    hl.setRecruitmentStatus(RecruitmentStatus.PLANNED);
                    hl.setNotes(wl.getJustification());
                    return hl;
                })
                .toList();

        List<HiringPlanLine> saved = hiringPlanLines.saveAll(lines);
        audit.record(MODULE, ENTITY, workforcePlanId.toString(),
                "GENERATE", null, saved.size() + " lines generated");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<HiringPlanLine> getByPlan(UUID workforcePlanId) {
        return hiringPlanLines.findByTenantIdAndWorkforcePlanIdOrderByTargetStartDateAsc(TenantContext.current(), workforcePlanId);
    }

    @Transactional
    public HiringPlanLine linkVacancy(UUID lineId, UUID vacancyId) {
        HiringPlanLine line = hiringPlanLines.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Hiring plan line not found"));

        if (!line.getTenantId().equals(TenantContext.current())) {
            throw new ResourceNotFoundException("Hiring plan line not found");
        }

        line.setVacancyId(vacancyId);
        if (line.getRecruitmentStatus() == RecruitmentStatus.PLANNED) {
            line.setRecruitmentStatus(RecruitmentStatus.VACANCY_OPEN);
        }

        HiringPlanLine saved = hiringPlanLines.save(line);
        audit.record(MODULE, ENTITY, lineId.toString(), "LINK_VACANCY", null, vacancyId);
        return saved;
    }

    @Transactional
    public HiringPlanLine updateStatus(UUID lineId, RecruitmentStatus status) {
        HiringPlanLine line = hiringPlanLines.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Hiring plan line not found"));

        if (!line.getTenantId().equals(TenantContext.current())) {
            throw new ResourceNotFoundException("Hiring plan line not found");
        }

        line.setRecruitmentStatus(status);
        HiringPlanLine saved = hiringPlanLines.save(line);
        audit.record(MODULE, ENTITY, lineId.toString(), "STATUS_UPDATE", null, status);
        return saved;
    }
}
