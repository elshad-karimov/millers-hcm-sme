package az.millers.hcm.learning.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.api.dto.LearningPathCourseRequest;
import az.millers.hcm.learning.api.dto.LearningPathRequest;
import az.millers.hcm.learning.api.dto.LearningPathResponse;
import az.millers.hcm.learning.domain.LearningPath;
import az.millers.hcm.learning.domain.LearningPathCourse;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.LearningPathCourseRepository;
import az.millers.hcm.learning.repo.LearningPathRepository;
import az.millers.hcm.common.BusinessNumbers;

/**
 * CRUD for learning paths (M46) and the path-advance lookup used by
 * {@link EnrollmentService} when an enrollment transitions to PASSED.
 */
@Service
public class LearningPathService {

    private final LearningPathRepository paths;
    private final LearningPathCourseRepository pathCourses;
    private final CourseRepository courses;

    public LearningPathService(LearningPathRepository paths,
                                LearningPathCourseRepository pathCourses,
                                CourseRepository courses) {
        this.paths = paths;
        this.pathCourses = pathCourses;
        this.courses = courses;
    }

    // ── List / Get ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LearningPathResponse> list(Boolean active) {
        List<LearningPath> all = active != null
                ? paths.findByActiveOrderByPathNoAsc(active)
                : paths.findAll();
        return all.stream().map(p -> toResponse(p,
                pathCourses.findByPathIdOrderByStepOrderAsc(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public LearningPathResponse get(UUID id) {
        LearningPath p = require(id);
        return toResponse(p, pathCourses.findByPathIdOrderByStepOrderAsc(id));
    }

    // ── Create / Update ───────────────────────────────────────────────────────

    @Transactional
    public LearningPathResponse create(LearningPathRequest req) {
        LearningPath p = new LearningPath();
        p.setPathNo(BusinessNumbers.format("PATH", 5, paths.nextNoSequence()));
        p.setName(req.name());
        p.setDescription(req.description());
        p.setActive(req.active());
        return toResponse(paths.save(p), List.of());
    }

    @Transactional
    public LearningPathResponse update(UUID id, LearningPathRequest req) {
        LearningPath p = require(id);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setActive(req.active());
        return toResponse(paths.save(p), pathCourses.findByPathIdOrderByStepOrderAsc(id));
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    @Transactional
    public LearningPathResponse addCourse(UUID pathId, LearningPathCourseRequest req) {
        LearningPath p = require(pathId);
        if (!courses.existsById(req.courseId())) {
            throw new BadRequestException("Course not found: " + req.courseId());
        }
        if (pathCourses.findByPathIdAndStepOrder(pathId, req.stepOrder()).isPresent()) {
            throw new BadRequestException(
                    "Step " + req.stepOrder() + " already exists in path " + p.getPathNo());
        }
        LearningPathCourse step = new LearningPathCourse();
        step.setPathId(pathId);
        step.setCourseId(req.courseId());
        step.setStepOrder(req.stepOrder());
        step.setRequiredToAdvance(req.requiredToAdvance());
        pathCourses.save(step);
        return toResponse(p, pathCourses.findByPathIdOrderByStepOrderAsc(pathId));
    }

    @Transactional
    public LearningPathResponse removeCourse(UUID pathId, UUID stepId) {
        LearningPathCourse step = pathCourses.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Path step not found: " + stepId));
        if (!step.getPathId().equals(pathId)) {
            throw new BadRequestException("Step " + stepId + " does not belong to path " + pathId);
        }
        pathCourses.delete(step);
        LearningPath p = require(pathId);
        return toResponse(p, pathCourses.findByPathIdOrderByStepOrderAsc(pathId));
    }

    // ── Path-advance engine ───────────────────────────────────────────────────

    /**
     * Called by {@link EnrollmentService} when an enrollment is PASSED.
     * Scans all path-steps referencing {@code completedCourseId}; for each step
     * whose {@code requiredToAdvance} is true, returns the course ID of the next
     * step (step_order + 1) in the same path, if one exists.
     *
     * <p>Returns the first matching next-course found. In the common case a course
     * belongs to at most one path, so this is deterministic.
     */
    public Optional<UUID> nextCourseInPath(UUID completedCourseId) {
        for (LearningPathCourse step : pathCourses.findByCourseId(completedCourseId)) {
            if (!step.isRequiredToAdvance()) continue;
            Optional<LearningPathCourse> next =
                    pathCourses.findByPathIdAndStepOrder(step.getPathId(), step.getStepOrder() + 1);
            if (next.isPresent()) {
                return Optional.of(next.get().getCourseId());
            }
        }
        return Optional.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LearningPath require(UUID id) {
        return paths.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Learning path not found: " + id));
    }

    private LearningPathResponse toResponse(LearningPath p, List<LearningPathCourse> steps) {
        return LearningPathResponse.from(p, steps);
    }
}
