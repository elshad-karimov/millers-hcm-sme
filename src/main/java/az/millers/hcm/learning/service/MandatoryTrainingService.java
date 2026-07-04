package az.millers.hcm.learning.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;
import az.millers.hcm.learning.domain.MandatoryTrainingRule;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EnrollmentRepository;
import az.millers.hcm.learning.repo.MandatoryTrainingRuleRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_14 M406 — mandatory / compliance training (PRD 14 §9/§16). The sweep
 * enrols every in-scope active employee who has never passed the course, and
 * RENEWS (existing enrolment back to ENROLLED with a fresh due date) anyone
 * whose last pass is older than the rule's recurrence window.
 */
@Service
public class MandatoryTrainingService {

    private static final Logger log = LoggerFactory.getLogger(MandatoryTrainingService.class);
    private static final String TENANT = "default";
    private static final String MODULE = "LEARNING";

    public record RuleRequest(UUID courseId, String name, String departmentName, UUID positionId,
                              UUID workLocationId, Integer recurrenceMonths, Integer dueDays,
                              Integer reminderDaysBefore, Boolean active) {}

    public record RuleCompliance(UUID ruleId, String ruleName, UUID courseId, long inScope,
                                 long compliant, long pending, long overdue) {}

    public record SweepResult(int rulesProcessed, int newlyEnrolled, int renewed) {}

    private final MandatoryTrainingRuleRepository rules;
    private final EnrollmentRepository enrollments;
    private final EnrollmentService enrollmentService;
    private final CourseRepository courses;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public MandatoryTrainingService(MandatoryTrainingRuleRepository rules,
                                    EnrollmentRepository enrollments,
                                    EnrollmentService enrollmentService,
                                    CourseRepository courses,
                                    NamedParameterJdbcTemplate jdbc,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.rules = rules;
        this.enrollments = enrollments;
        this.enrollmentService = enrollmentService;
        this.courses = courses;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Rules CRUD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MandatoryTrainingRule> list() {
        return rules.findByTenantIdOrderByNameAsc(TENANT);
    }

    @Transactional
    public MandatoryTrainingRule save(UUID id, RuleRequest req) {
        if (req.courseId() == null || !courses.existsById(req.courseId())) {
            throw new BadRequestException("Course not found: " + req.courseId());
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("A rule name is required");
        }
        if (req.recurrenceMonths() != null && req.recurrenceMonths() <= 0) {
            throw new BadRequestException("recurrenceMonths must be positive");
        }
        MandatoryTrainingRule r;
        if (id == null) {
            r = new MandatoryTrainingRule();
            r.setTenantId(TENANT);
            r.setCreatedBy(currentRequest.username());
        } else {
            r = rules.findById(id).orElseThrow(
                    () -> new ResourceNotFoundException("Rule not found: " + id));
        }
        r.setCourseId(req.courseId());
        r.setName(req.name().trim());
        r.setDepartmentName(req.departmentName());
        r.setPositionId(req.positionId());
        r.setWorkLocationId(req.workLocationId());
        r.setRecurrenceMonths(req.recurrenceMonths());
        if (req.dueDays() != null) r.setDueDays(req.dueDays());
        if (req.reminderDaysBefore() != null) r.setReminderDaysBefore(req.reminderDaysBefore());
        if (req.active() != null) r.setActive(req.active());
        MandatoryTrainingRule saved = rules.save(r);
        audit.record(MODULE, "MandatoryTrainingRule", saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE", null, req.name().trim());
        return saved;
    }

    // ── Sweep ───────────────────────────────────────────────────────────────

    /** Daily 05:30 — auto-assign + renew per rule. */
    @Scheduled(cron = "0 30 5 * * *")
    public void dailySweep() {
        try {
            SweepResult r = runSweep();
            if (r.newlyEnrolled() > 0 || r.renewed() > 0) {
                log.info("Mandatory-training sweep: {} rules, {} enrolled, {} renewed",
                        r.rulesProcessed(), r.newlyEnrolled(), r.renewed());
            }
        } catch (Exception ex) {
            log.error("Mandatory-training sweep failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public SweepResult runSweep() {
        int enrolled = 0, renewed = 0;
        List<MandatoryTrainingRule> active = rules.findByTenantIdAndActiveTrueOrderByNameAsc(TENANT);
        for (MandatoryTrainingRule rule : active) {
            for (UUID employeeId : audienceOf(rule)) {
                Enrollment existing = enrollments
                        .findByCourseIdAndEmployeeId(rule.getCourseId(), employeeId).orElse(null);
                LocalDate due = LocalDate.now().plusDays(rule.getDueDays());
                if (existing == null) {
                    try {
                        enrollmentService.enroll(new EnrollRequest(
                                rule.getCourseId(), employeeId, EnrolledVia.ASSIGNED, due));
                        enrolled++;
                    } catch (Exception ex) {
                        // unpublished course / race — skip this employee, keep sweeping
                        log.warn("Mandatory enrol failed for {} on rule {}: {}",
                                employeeId, rule.getName(), ex.getMessage());
                    }
                } else if (needsRenewal(existing, rule)) {
                    existing.setStatus(EnrollmentStatus.ENROLLED);
                    existing.setDueDate(due);
                    enrollments.save(existing);
                    renewed++;
                    audit.record(MODULE, "Enrollment", existing.getId().toString(), "RENEW",
                            null, Map.of("rule", rule.getName(), "dueDate", due.toString()));
                }
            }
        }
        return new SweepResult(active.size(), enrolled, renewed);
    }

    /** PASSED and older than the recurrence window → must re-complete. */
    private static boolean needsRenewal(Enrollment e, MandatoryTrainingRule rule) {
        if (rule.getRecurrenceMonths() == null) return false;
        if (e.getStatus() != EnrollmentStatus.PASSED || e.getCompletedAt() == null) return false;
        return e.getCompletedAt().isBefore(OffsetDateTime.now().minusMonths(rule.getRecurrenceMonths()));
    }

    /** Active employees matching the rule's scope filters (non-encrypted projection). */
    private List<UUID> audienceOf(MandatoryTrainingRule rule) {
        StringBuilder sql = new StringBuilder(
                "SELECT id::text AS id FROM core_hr.employee WHERE status = 'ACTIVE'");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (rule.getDepartmentName() != null && !rule.getDepartmentName().isBlank()) {
            sql.append(" AND department_name = :dept");
            params.addValue("dept", rule.getDepartmentName());
        }
        if (rule.getPositionId() != null) {
            sql.append(" AND position_id = :pos");
            params.addValue("pos", rule.getPositionId());
        }
        if (rule.getWorkLocationId() != null) {
            sql.append(" AND work_location_id = :loc");
            params.addValue("loc", rule.getWorkLocationId());
        }
        List<UUID> ids = new ArrayList<>();
        jdbc.queryForList(sql.toString(), params)
                .forEach(row -> ids.add(UUID.fromString((String) row.get("id"))));
        return ids;
    }

    // ── Compliance summary ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RuleCompliance> compliance() {
        List<RuleCompliance> out = new ArrayList<>();
        for (MandatoryTrainingRule rule : rules.findByTenantIdAndActiveTrueOrderByNameAsc(TENANT)) {
            List<UUID> audience = audienceOf(rule);
            long compliant = 0, pending = 0, overdue = 0;
            for (UUID employeeId : audience) {
                Enrollment e = enrollments
                        .findByCourseIdAndEmployeeId(rule.getCourseId(), employeeId).orElse(null);
                if (e != null && e.getStatus() == EnrollmentStatus.PASSED && !needsRenewal(e, rule)) {
                    compliant++;
                } else if (e != null && e.getDueDate() != null
                        && e.getDueDate().isBefore(LocalDate.now())
                        && e.getStatus() != EnrollmentStatus.PASSED) {
                    overdue++;
                } else {
                    pending++;
                }
            }
            out.add(new RuleCompliance(rule.getId(), rule.getName(), rule.getCourseId(),
                    audience.size(), compliant, pending, overdue));
        }
        return out;
    }
}
