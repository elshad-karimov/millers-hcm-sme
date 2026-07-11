package az.millers.hcm.learning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.learning.event.CourseAssignedEvent;
import az.millers.hcm.learning.event.CourseFailedEvent;
import az.millers.hcm.learning.event.CoursePassedEvent;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.AttemptResponse;
import az.millers.hcm.learning.api.dto.AttemptSubmitRequest;
import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.api.dto.EnrollmentResponse;
import az.millers.hcm.learning.domain.AttemptStatus;
import az.millers.hcm.learning.domain.Certificate;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCompetency;
import az.millers.hcm.learning.domain.CourseStatus;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;
import az.millers.hcm.learning.domain.QuestionType;
import az.millers.hcm.learning.domain.QuizAnswer;
import az.millers.hcm.learning.domain.QuizAttempt;
import az.millers.hcm.learning.domain.QuizQuestion;
import az.millers.hcm.learning.repo.CertificateRepository;
import az.millers.hcm.learning.repo.CourseCompetencyRepository;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.learning.repo.EnrollmentRepository;
import az.millers.hcm.learning.repo.QuizAnswerRepository;
import az.millers.hcm.learning.repo.QuizAttemptRepository;
import az.millers.hcm.learning.repo.QuizQuestionRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Enrollment + quiz-attempt service (PRD 8.14).
 *
 * <p>Lifecycle: enroll → start attempt → submit attempt → auto-grade →
 * pass/fail. On PASSED, the service auto-issues a {@link Certificate}
 * (with valid-until computed from {@code course.validForMonths}) and
 * awards every mapped {@link CourseCompetency} as an
 * {@link EmployeeCompetency} (source=COURSE, source_ref=enrollment_id).
 *
 * <p>Multiple attempts retain the best score; once max_attempts is consumed
 * without passing, the enrollment is locked in FAILED.
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);
    private static final String MODULE = "LEARNING";

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final QuizQuestionRepository questions;
    private final QuizAttemptRepository attempts;
    private final QuizAnswerRepository answers;
    private final CertificateRepository certificates;
    private final CourseCompetencyRepository courseCompetencies;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final EmployeeRepository employees;
    private final LearningPathService learningPaths;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ApplicationEventPublisher events;

    public EnrollmentService(EnrollmentRepository enrollments,
                              CourseRepository courses,
                              QuizQuestionRepository questions,
                              QuizAttemptRepository attempts,
                              QuizAnswerRepository answers,
                              CertificateRepository certificates,
                              CourseCompetencyRepository courseCompetencies,
                              EmployeeCompetencyRepository employeeCompetencies,
                              EmployeeRepository employees,
                              LearningPathService learningPaths,
                              AuditService audit,
                              CurrentRequest currentRequest,
                              ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.certificates = certificates;
        this.courseCompetencies = courseCompetencies;
        this.employeeCompetencies = employeeCompetencies;
        this.employees = employees;
        this.learningPaths = learningPaths;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Enrollment get(UUID id) {
        return enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Enrollment> list(UUID courseId, UUID employeeId, EnrollmentStatus status,
                                   Pageable pageable) {
        if (courseId != null)   return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId, pageable);
        if (employeeId != null) return enrollments.findByEmployeeIdOrderByEnrolledAtDesc(employeeId, pageable);
        if (status != null)     return enrollments.findByStatusOrderByEnrolledAtDesc(status, pageable);
        return enrollments.findAllByOrderByEnrolledAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> attemptsFor(UUID enrollmentId) {
        return attempts.findByEnrollmentIdOrderByAttemptNoAsc(enrollmentId);
    }

    @Transactional(readOnly = true)
    public AttemptResponse getAttempt(UUID attemptId) {
        QuizAttempt a = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found: " + attemptId));
        return AttemptResponse.from(a, answers.findByAttemptIdOrderById(attemptId));
    }

    @Transactional
    public Enrollment enroll(EnrollRequest req) {
        Course course = courses.findById(req.courseId())
                .orElseThrow(() -> new BadRequestException("Course not found: " + req.courseId()));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BadRequestException(
                    "Course must be PUBLISHED to enroll (was " + course.getStatus() + ")");
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        enrollments.findByCourseIdAndEmployeeId(req.courseId(), req.employeeId())
                .ifPresent(e -> {
                    throw new BadRequestException(
                            "Employee is already enrolled in this course: " + e.getEnrollmentNo());
                });

        Enrollment e = new Enrollment();
        e.setEnrollmentNo(BusinessNumbers.format("ENR", 5, enrollments.nextNoSequence()));
        e.setCourseId(req.courseId());
        e.setEmployeeId(req.employeeId());
        e.setStatus(EnrollmentStatus.ENROLLED);
        e.setDueDate(req.dueDate());
        e.setEnrolledVia(req.enrolledVia() == null ? EnrolledVia.SELF_ENROLLED : req.enrolledVia());
        if (e.getEnrolledVia() == EnrolledVia.ASSIGNED) {
            e.setAssignedBy(currentRequest.username());
        }
        Enrollment saved = enrollments.save(e);
        audit.record(MODULE, "Enrollment", saved.getId().toString(),
                "ENROLL", null, EnrollmentResponse.from(saved));
        events.publishEvent(new CourseAssignedEvent(
                saved.getEmployeeId(), course.getId(), course.getTitle(), saved.getId()));
        return saved;
    }

    @Transactional
    public QuizAttempt startAttempt(UUID enrollmentId) {
        Enrollment e = get(enrollmentId);
        if (e.getStatus() == EnrollmentStatus.PASSED) {
            throw new BadRequestException("Enrollment has already been passed");
        }
        if (e.getStatus() == EnrollmentStatus.FAILED) {
            throw new BadRequestException("No attempts remaining — enrollment is FAILED");
        }
        Course course = courses.findById(e.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course missing: " + e.getCourseId()));
        if (e.getAttemptsUsed() >= course.getMaxAttempts()) {
            throw new BadRequestException(
                    "No attempts remaining (max=" + course.getMaxAttempts() + ")");
        }

        // Reject if there is already an open attempt.
        for (QuizAttempt prev : attempts.findByEnrollmentIdOrderByAttemptNoAsc(enrollmentId)) {
            if (prev.getStatus() == AttemptStatus.IN_PROGRESS) {
                return prev;
            }
        }

        QuizAttempt a = new QuizAttempt();
        a.setEnrollmentId(enrollmentId);
        a.setAttemptNo(e.getAttemptsUsed() + 1);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setPassingScore(course.getPassingScore());
        QuizAttempt saved = attempts.save(a);

        if (e.getStartedAt() == null) e.setStartedAt(OffsetDateTime.now());
        e.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollments.save(e);

        audit.record(MODULE, "QuizAttempt", saved.getId().toString(),
                "START", null, "attempt " + saved.getAttemptNo());
        return saved;
    }

    /**
     * Submit + auto-grade a quiz attempt. Returns the graded result + per-answer
     * breakdown. Updates the enrollment's best score and (on pass) issues a
     * certificate + awards mapped competencies (PRD 8.14 acceptance).
     */
    @Transactional
    public AttemptResponse submitAttempt(UUID attemptId, AttemptSubmitRequest req) {
        QuizAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found: " + attemptId));
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt is no longer in progress");
        }
        Enrollment enrollment = get(attempt.getEnrollmentId());
        Course course = courses.findById(enrollment.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course missing: " + enrollment.getCourseId()));

        List<QuizQuestion> qs = questions.findByCourseIdOrderByQuestionNoAsc(course.getId());
        if (qs.isEmpty()) {
            throw new BadRequestException("Course has no quiz questions");
        }

        Map<UUID, AttemptSubmitRequest.Answer> byQ = new java.util.HashMap<>();
        for (AttemptSubmitRequest.Answer a : req.answers()) {
            byQ.put(a.questionId(), a);
        }

        int total = 0;
        BigDecimal earned = BigDecimal.ZERO;
        List<QuizAnswer> rows = new ArrayList<>(qs.size());
        for (QuizQuestion q : qs) {
            total += q.getPoints();
            AttemptSubmitRequest.Answer a = byQ.get(q.getId());
            List<String> selected = a == null ? List.of() : new ArrayList<>(a.selectedKeys());
            // M46: MULTI_SELECT awards proportional partial credit.
            // Other types are all-or-nothing (set equality).
            BigDecimal pts = score(q, selected);
            boolean correct = pts.compareTo(BigDecimal.valueOf(q.getPoints())) == 0;
            earned = earned.add(pts);
            QuizAnswer ans = new QuizAnswer();
            ans.setAttemptId(attempt.getId());
            ans.setQuestionId(q.getId());
            ans.setSelectedKeys(selected);
            ans.setCorrect(correct);
            ans.setPointsAwarded(pts);
            rows.add(ans);
        }
        answers.saveAll(rows);

        BigDecimal percent = total == 0
                ? BigDecimal.ZERO
                : earned.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        boolean passed = percent.compareTo(BigDecimal.valueOf(course.getPassingScore())) >= 0;

        attempt.setStatus(passed ? AttemptStatus.PASSED : AttemptStatus.FAILED);
        attempt.setSubmittedAt(OffsetDateTime.now());
        attempt.setTotalPoints(total);
        attempt.setEarnedPoints(earned);
        attempt.setScorePercent(percent);
        attempts.save(attempt);

        // Update enrollment counters
        enrollment.setAttemptsUsed(enrollment.getAttemptsUsed() + 1);
        enrollment.setLastScorePercent(percent);
        enrollment.setLastAttemptAt(OffsetDateTime.now());
        if (enrollment.getBestScorePercent() == null
                || percent.compareTo(enrollment.getBestScorePercent()) > 0) {
            enrollment.setBestScorePercent(percent);
        }
        if (passed) {
            enrollment.setStatus(EnrollmentStatus.PASSED);
            enrollment.setCompletedAt(OffsetDateTime.now());
            issueCertificate(enrollment, course, percent);
            awardCompetencies(enrollment, course);
            advanceLearningPath(enrollment, course);
            // M49: notify listeners (e.g. GoalAutoRatingService) that a course was passed.
            events.publishEvent(new CoursePassedEvent(
                    enrollment.getEmployeeId(), course.getId(), enrollment.getBestScorePercent()));
        } else if (enrollment.getAttemptsUsed() >= course.getMaxAttempts()) {
            enrollment.setStatus(EnrollmentStatus.FAILED);
            events.publishEvent(new CourseFailedEvent(
                    enrollment.getEmployeeId(), course.getId(), course.getTitle(),
                    enrollment.getBestScorePercent()));
        } else {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        }
        enrollments.save(enrollment);

        audit.record(MODULE, "QuizAttempt", attempt.getId().toString(),
                passed ? "PASS" : "FAIL", null,
                Map.of("percent", percent.toPlainString(),
                        "earned", earned.toPlainString(),
                        "total", String.valueOf(total)));

        return AttemptResponse.from(attempt, rows);
    }

    private void issueCertificate(Enrollment enrollment, Course course, BigDecimal percent) {
        if (certificates.findByEnrollmentId(enrollment.getId()).isPresent()) return;
        Certificate cert = new Certificate();
        cert.setCertificateNo(BusinessNumbers.format("CERT", 5, certificates.nextNoSequence()));
        cert.setEnrollmentId(enrollment.getId());
        cert.setCourseId(course.getId());
        cert.setEmployeeId(enrollment.getEmployeeId());
        cert.setScorePercent(percent);
        if (course.getValidForMonths() != null) {
            cert.setValidUntil(LocalDate.now().plusMonths(course.getValidForMonths()));
        }
        Certificate saved = certificates.save(cert);
        audit.record(MODULE, "Certificate", saved.getId().toString(),
                "ISSUE", null,
                Map.of("certificateNo", saved.getCertificateNo(),
                        "score", percent.toPlainString(),
                        "validUntil", saved.getValidUntil() == null ? "" : saved.getValidUntil().toString()));
    }

    private void awardCompetencies(Enrollment enrollment, Course course) {
        List<CourseCompetency> mapping = courseCompetencies.findByCourseId(course.getId());
        for (CourseCompetency m : mapping) {
            Optional<EmployeeCompetency> existing =
                    employeeCompetencies.findByEmployeeIdAndCompetencyIdAndSourceRef(
                            enrollment.getEmployeeId(), m.getCompetencyId(), enrollment.getId());
            if (existing.isPresent()) continue;
            EmployeeCompetency ec = new EmployeeCompetency();
            ec.setEmployeeId(enrollment.getEmployeeId());
            ec.setCompetencyId(m.getCompetencyId());
            ec.setProficiency(m.getAwardedLevel());
            ec.setSource("COURSE");
            ec.setSourceRef(enrollment.getId());
            if (course.getValidForMonths() != null) {
                ec.setValidUntil(LocalDate.now().plusMonths(course.getValidForMonths()));
            }
            employeeCompetencies.save(ec);
            audit.record(MODULE, "EmployeeCompetency", ec.getId().toString(),
                    "AWARD", null,
                    Map.of("employeeId", ec.getEmployeeId().toString(),
                            "competencyId", ec.getCompetencyId().toString(),
                            "level", String.valueOf(ec.getProficiency()),
                            "source", ec.getSource()));
        }
    }

    /**
     * Computes the points earned for a single answer.
     *
     * <p><b>MULTIPLE_CHOICE / TRUE_FALSE</b> — full-or-nothing: awards
     * {@code q.getPoints()} when the selected set exactly equals the correct set,
     * {@code 0} otherwise.
     *
     * <p><b>MULTI_SELECT</b> (M46 partial credit) — proportional with a penalty
     * for wrong selections:
     * <pre>
     *   hits   = |selected ∩ correctKeys|
     *   extras = |selected \ correctKeys|
     *   score  = max(0, hits − extras) / |correctKeys| × questionPoints
     * </pre>
     * Selecting every correct key and no wrong ones earns full marks.
     * Selecting extra wrong keys deducts from the hit count.
     */
    private static BigDecimal score(QuizQuestion q, List<String> selected) {
        List<String> sel = (selected == null) ? List.of() : selected;
        List<String> cor = (q.getCorrectKeys() == null) ? List.of() : q.getCorrectKeys();

        if (q.getQuestionType() != QuestionType.MULTI_SELECT) {
            // All-or-nothing for MULTIPLE_CHOICE and TRUE_FALSE.
            Set<String> a = new HashSet<>(sel);
            Set<String> b = new HashSet<>(cor);
            return a.equals(b) ? BigDecimal.valueOf(q.getPoints()) : BigDecimal.ZERO;
        }

        // Partial credit for MULTI_SELECT.
        Set<String> correctSet = new HashSet<>(cor);
        if (correctSet.isEmpty()) return BigDecimal.ZERO;

        long hits   = sel.stream().filter(correctSet::contains).count();
        long extras = sel.stream().filter(k -> !correctSet.contains(k)).count();
        long net    = Math.max(0L, hits - extras);

        return BigDecimal.valueOf(net)
                .multiply(BigDecimal.valueOf(q.getPoints()))
                .divide(BigDecimal.valueOf(correctSet.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * After an enrollment is PASSED, checks whether the completed course is a
     * required step in any learning path. If so, auto-enrolls the employee in
     * the next step (enrolled_via = PATH). Silently skips if already enrolled.
     */
    private void advanceLearningPath(Enrollment enrollment, Course course) {
        learningPaths.nextCourseInPath(course.getId()).ifPresent(nextCourseId -> {
            boolean alreadyEnrolled =
                    enrollments.findByCourseIdAndEmployeeId(nextCourseId, enrollment.getEmployeeId())
                            .isPresent();
            if (alreadyEnrolled) {
                log.debug("Path advance skipped — employee {} already enrolled in course {}",
                        enrollment.getEmployeeId(), nextCourseId);
                return;
            }
            try {
                EnrollRequest req = new EnrollRequest(
                        nextCourseId, enrollment.getEmployeeId(), EnrolledVia.PATH, null);
                enroll(req);
                log.info("Path advance: enrolled employee {} in course {} (via PATH)",
                        enrollment.getEmployeeId(), nextCourseId);
            } catch (BadRequestException ex) {
                // Non-fatal: log and continue (e.g. course not PUBLISHED yet).
                log.warn("Path advance failed for employee {} → course {}: {}",
                        enrollment.getEmployeeId(), nextCourseId, ex.getMessage());
            }
        });
    }
}
