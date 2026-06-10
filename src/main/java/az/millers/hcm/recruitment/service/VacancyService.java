package az.millers.hcm.recruitment.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.VacancyRequest;
import az.millers.hcm.recruitment.api.dto.VacancyResponse;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.service.PositionHeadcountService;

@Service
public class VacancyService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Vacancy";

    private final VacancyRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final PositionHeadcountService headcountGate;

    public VacancyService(VacancyRepository repository, AuditService audit,
                          CurrentRequest currentRequest,
                          PositionHeadcountService headcountGate) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.headcountGate = headcountGate;
    }

    @Transactional(readOnly = true)
    public Vacancy get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacancy not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Vacancy> list(VacancyStatus status, Pageable pageable) {
        if (status != null) return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public Vacancy create(VacancyRequest req) {
        // M109 — refuse to post requisitions for a position that has no room.
        int openings = req.openings() == null ? 1 : req.openings();
        headcountGate.assertCanPostVacancy(req.positionId(), openings);

        Vacancy v = new Vacancy();
        v.setVacancyNo(String.format("VAC-%05d", repository.nextNoSequence()));
        v.setStatus(VacancyStatus.OPEN);
        v.setCreatedBy(currentRequest.username());
        v.setUpdatedBy(currentRequest.username());
        applyRequest(v, req);
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, VacancyResponse.from(saved));
        return saved;
    }

    @Transactional
    public Vacancy update(UUID id, VacancyRequest req) {
        Vacancy v = get(id);
        VacancyResponse before = VacancyResponse.from(v);
        applyRequest(v, req);
        v.setUpdatedBy(currentRequest.username());
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, VacancyResponse.from(saved));
        return saved;
    }

    @Transactional
    public Vacancy changeStatus(UUID id, VacancyStatus newStatus, String reason) {
        Vacancy v = get(id);
        if (v.getStatus() == newStatus) {
            throw new BadRequestException("Vacancy already " + newStatus);
        }
        VacancyStatus old = v.getStatus();
        v.setStatus(newStatus);
        v.setUpdatedBy(currentRequest.username());
        Vacancy saved = repository.save(v);
        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                java.util.Map.of("status", old.name()),
                java.util.Map.of("status", newStatus.name(),
                        "reason", reason == null ? "" : reason));
        return saved;
    }

    private void applyRequest(Vacancy v, VacancyRequest req) {
        v.setTitle(req.title());
        v.setPositionId(req.positionId());
        v.setDepartment(req.department());
        v.setLocation(req.location());
        v.setOpenings(req.openings() == null ? 1 : req.openings());
        v.setDescription(req.description());
        v.setRequirements(req.requirements());
        if (req.salaryMin() != null && req.salaryMax() != null
                && req.salaryMin().compareTo(req.salaryMax()) > 0) {
            throw new BadRequestException("salaryMin cannot be greater than salaryMax");
        }
        v.setSalaryMin(req.salaryMin());
        v.setSalaryMax(req.salaryMax());
        v.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        v.setHiringManagerId(req.hiringManagerId());
        v.setRecruiterId(req.recruiterId());
        v.setOpeningDate(req.openingDate());
        v.setClosingDate(req.closingDate());
        // M274 — requisition fields. Type defaults to NEW_HEADCOUNT so
        // pre-M274 clients that don't send it keep working.
        v.setRequisitionType(req.requisitionType() == null
                ? az.millers.hcm.recruitment.domain.RequisitionType.NEW_HEADCOUNT
                : req.requisitionType());
        v.setHiringReason(req.hiringReason());
        v.setTargetStartDate(req.targetStartDate());
        v.setCostCentre(req.costCentre());
        v.setEmploymentType(req.employmentType());
        v.setReplacedEmployeeId(req.replacedEmployeeId());
    }
}
