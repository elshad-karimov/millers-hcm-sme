package az.millers.hcm.staffing.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.CareerPath;
import az.millers.hcm.staffing.domain.CareerPathStep;
import az.millers.hcm.staffing.repo.CareerPathRepository;
import az.millers.hcm.staffing.repo.CareerPathStepRepository;
import jakarta.persistence.EntityManager;

/**
 * HCM_16 M416 — career path management (PRD §16.6).
 * Transparent to employees (read); HR-only write.
 */
@Service
public class CareerPathService {

    private final CareerPathRepository paths;
    private final CareerPathStepRepository steps;
    private final EntityManager em;
    private final CurrentRequest currentRequest;

    public CareerPathService(CareerPathRepository paths,
                              CareerPathStepRepository steps,
                              EntityManager em,
                              CurrentRequest currentRequest) {
        this.paths = paths;
        this.steps = steps;
        this.em = em;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CareerPathResponse> list() {
        String tenant = "default";
        return paths.findByTenantIdOrderByCodeAsc(tenant).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerPathResponse get(UUID id) {
        CareerPath path = paths.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career path not found: " + id));
        return toResponse(path);
    }

    @Transactional
    public CareerPathResponse create(CareerPathRequest req) {
        if (req.code == null || req.name == null) {
            throw new BadRequestException("code and name are required");
        }
        String tenant = "default";
        if (paths.findByTenantIdAndCode(tenant, req.code).isPresent()) {
            throw new BadRequestException("Career path code already exists: " + req.code);
        }

        CareerPath path = new CareerPath();
        path.setCode(req.code);
        path.setName(req.name);
        path.setJobFamily(req.jobFamily);
        path.setDescription(req.description);
        path.setActive(req.active != null ? req.active : true);
        path.setCreatedBy(currentRequest.username());
        paths.save(path);

        // Create steps
        if (req.steps != null) {
            for (int i = 0; i < req.steps.size(); i++) {
                StepRequest s = req.steps.get(i);
                CareerPathStep step = new CareerPathStep();
                step.setPathId(path.getId());
                step.setStepOrder(i + 1);
                step.setFromPositionId(s.fromPositionId);
                step.setToPositionId(s.toPositionId);
                step.setRequiredSkills(s.requiredSkills);
                step.setRequiredCertifications(s.requiredCertifications);
                step.setRequiredExperienceYears(s.requiredExperienceYears);
                step.setRequiredCourses(s.requiredCourses);
                step.setTypicalTenureMonths(s.typicalTenureMonths);
                steps.save(step);
            }
        }

        return toResponse(path);
    }

    @Transactional
    public CareerPathResponse update(UUID id, CareerPathRequest req) {
        CareerPath path = paths.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career path not found: " + id));

        if (req.name != null) path.setName(req.name);
        if (req.jobFamily != null) path.setJobFamily(req.jobFamily);
        if (req.description != null) path.setDescription(req.description);
        if (req.active != null) path.setActive(req.active);

        // Replace steps (delete + flush + reinsert pattern to avoid unique-index bug)
        steps.deleteByPathId(id);
        em.flush();

        if (req.steps != null) {
            for (int i = 0; i < req.steps.size(); i++) {
                StepRequest s = req.steps.get(i);
                CareerPathStep step = new CareerPathStep();
                step.setPathId(id);
                step.setStepOrder(i + 1);
                step.setFromPositionId(s.fromPositionId);
                step.setToPositionId(s.toPositionId);
                step.setRequiredSkills(s.requiredSkills);
                step.setRequiredCertifications(s.requiredCertifications);
                step.setRequiredExperienceYears(s.requiredExperienceYears);
                step.setRequiredCourses(s.requiredCourses);
                step.setTypicalTenureMonths(s.typicalTenureMonths);
                steps.save(step);
            }
        }

        paths.save(path);
        return toResponse(path);
    }

    private CareerPathResponse toResponse(CareerPath path) {
        List<CareerPathStep> stepList = steps.findByPathIdOrderByStepOrderAsc(path.getId());
        List<StepResponse> stepResponses = stepList.stream()
                .map(s -> new StepResponse(
                        s.getId(),
                        s.getStepOrder(),
                        s.getFromPositionId(),
                        s.getToPositionId(),
                        s.getRequiredSkills(),
                        s.getRequiredCertifications(),
                        s.getRequiredExperienceYears(),
                        s.getRequiredCourses(),
                        s.getTypicalTenureMonths()
                ))
                .toList();
        return new CareerPathResponse(
                path.getId(),
                path.getCode(),
                path.getName(),
                path.getJobFamily(),
                path.getDescription(),
                path.getActive(),
                stepResponses
        );
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record CareerPathRequest(
            String code,
            String name,
            String jobFamily,
            String description,
            Boolean active,
            List<StepRequest> steps) {}

    public record StepRequest(
            UUID fromPositionId,
            UUID toPositionId,
            String requiredSkills,
            String requiredCertifications,
            Integer requiredExperienceYears,
            String requiredCourses,
            Integer typicalTenureMonths) {}

    public record CareerPathResponse(
            UUID id,
            String code,
            String name,
            String jobFamily,
            String description,
            Boolean active,
            List<StepResponse> steps) {}

    public record StepResponse(
            UUID id,
            Integer stepOrder,
            UUID fromPositionId,
            UUID toPositionId,
            String requiredSkills,
            String requiredCertifications,
            Integer requiredExperienceYears,
            String requiredCourses,
            Integer typicalTenureMonths) {}
}
