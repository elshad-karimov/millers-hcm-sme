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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
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
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EnrollmentService(EnrollmentRepository enrollments,
                              CourseRepository courses,
                              QuizQuestionRepository questions,
                              QuizAttemptRepository attempts,
                              QuizAnswerRepository answers,
                              CertificateRepository certificates,
                              CourseCompetencyRepository courseCompetencies,
                              EmployeeCompetencyRepository employeeCompetencies,
                              EmployeeRepository employees,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.certificates = certificates;
        this.courseCompetencies = courseCompetencies;
        this.employeeCompetencies = employeeCompetencies;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
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
        e.setEnrollmentNo(String.format("ENR-%05d", enrollments.nextNoSequence()));
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

        int total = 0, earned = 0;
        List<QuizAnswer> rows = new ArrayList<>(qs.size());
        for (QuizQuestion q : qs) {
            total += q.getPoints();
            AttemptSubmitRequest.Answer a = byQ.get(q.getId());
            List<String> selected = a == null ? List.of() : new ArrayList<>(a.selectedKeys());
            boolean correct = matches(selected, q.getCorrectKeys());
            int pts = correct ? q.getPoints() : 0;
            earned += pts;
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
                : BigDecimal.valueOf(earned)
                        .multiply(BigDecimal.valueOf(100))
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
        } else if (enrollment.getAttemptsUsed() >= course.getMaxAttempts()) {
            enrollment.setStatus(EnrollmentStatus.FAILED);
        } else {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        }
        enrollments.save(enrollment);

        audit.record(MODULE, "QuizAttempt", attempt.getId().toString(),
                passed ? "PASS" : "FAIL", null,
                Map.of("percent", percent.toPlainString(),
                        "earned", String.valueOf(earned),
                        "total", String.valueOf(total)));

        return AttemptResponse.from(attempt, rows);
    }

    private void issueCertificate(Enrollment enrollment, Course course, BigDecimal percent) {
        if (certificates.findByEnrollmentId(enrollment.getId()).isPresent()) return;
        Certificate cert = new Certificate();
        cert.setCertificateNo(String.format("CERT-%05d", certificates.nextNoSequence()));
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

    /** Returns true iff selected keys == correct keys (set equality). */
    private static boolean matches(List<String> selected, List<String> correct) {
        if (selected == null) selected = List.of();
        if (correct == null)  correct = List.of();
        Set<String> a = new HashSet<>(selected);
        Set<String> b = new HashSet<>(correct);
        return a.equals(b);
    }

    @SuppressWarnings("unused")
    private static void unused(Logger l) { l.debug(""); }
}
