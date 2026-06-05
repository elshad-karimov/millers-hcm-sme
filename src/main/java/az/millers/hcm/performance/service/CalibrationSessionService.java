package az.millers.hcm.performance.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.api.dto.CalibrationBoardEntry;

import az.millers.hcm.performance.api.dto.CalibrationBoardResponse;
import az.millers.hcm.performance.api.dto.CalibrationRequest;
import az.millers.hcm.performance.api.dto.CalibrationSessionRequest;
import az.millers.hcm.performance.api.dto.CalibrationSessionResponse;
import az.millers.hcm.performance.domain.CalibrationSession;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.CalibrationSessionRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Calibration session layer — structured facilitated meetings that group all
 * {@link PerformanceReview} records for a cycle and track the session lifecycle
 * (PRD §8.13 – M56).
 *
 * <p>Session lifecycle: {@code SCHEDULED → IN_PROGRESS → COMPLETED}
 */
@Service
public class CalibrationSessionService {

    private static final String STATUS_SCHEDULED    = "SCHEDULED";
    private static final String STATUS_IN_PROGRESS  = "IN_PROGRESS";
    private static final String STATUS_COMPLETED    = "COMPLETED";

    private final CalibrationSessionRepository sessions;
    private final ReviewCycleRepository cycles;
    private final PerformanceReviewRepository reviews;
    private final NamedParameterJdbcTemplate jdbc;
    private final PerformanceReviewService reviewService;
    private final CurrentRequest currentRequest;
    /** M121 — target-distribution lookup + edit-log writes. */
    private final az.millers.hcm.performance.repo.CycleCalibrationTargetRepository targets;
    private final az.millers.hcm.performance.repo.CalibrationEditLogRepository editLogs;

    public CalibrationSessionService(CalibrationSessionRepository sessions,
                                     ReviewCycleRepository cycles,
                                     PerformanceReviewRepository reviews,
                                     NamedParameterJdbcTemplate jdbc,
                                     PerformanceReviewService reviewService,
                                     CurrentRequest currentRequest,
                                     az.millers.hcm.performance.repo.CycleCalibrationTargetRepository targets,
                                     az.millers.hcm.performance.repo.CalibrationEditLogRepository editLogs) {
        this.sessions = sessions;
        this.cycles = cycles;
        this.reviews = reviews;
        this.jdbc = jdbc;
        this.reviewService = reviewService;
        this.currentRequest = currentRequest;
        this.targets = targets;
        this.editLogs = editLogs;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create / list sessions
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public CalibrationSessionResponse createSession(UUID cycleId, CalibrationSessionRequest req) {
        cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + cycleId));

        CalibrationSession session = new CalibrationSession();
        session.setCycleId(cycleId);
        session.setName(req.name());
        session.setScheduledAt(req.scheduledAt());
        session.setFacilitator(req.facilitator());
        session.setNotes(req.notes());
        session.setCreatedBy(currentRequest.username());

        return CalibrationSessionResponse.from(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<CalibrationSessionResponse> listSessions(UUID cycleId) {
        cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + cycleId));
        return sessions.findByCycleIdOrderByScheduledAtDesc(cycleId)
                .stream()
                .map(CalibrationSessionResponse::from)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Board view
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CalibrationBoardResponse getBoard(UUID cycleId) {
        ReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + cycleId));

        List<PerformanceReview> cycleReviews =
                reviews.findByCycleIdOrderByCreatedAtDesc(cycleId);

        // Use a lightweight JDBC projection to avoid triggering EncryptedStringConverter
        // on national_id / bank_account columns inside the Employee JPA entity.
        List<UUID> employeeIds = cycleReviews.stream()
                .map(PerformanceReview::getEmployeeId)
                .distinct()
                .collect(Collectors.toList());

        // name-only projection — only non-encrypted columns
        record EmpInfo(String firstName, String lastName, String department) {}
        Map<UUID, EmpInfo> empMap;
        if (employeeIds.isEmpty()) {
            empMap = Map.of();
        } else {
            empMap = jdbc.queryForList(
                    "SELECT id::text AS id, first_name, last_name, department_name "
                            + "  FROM core_hr.employee "
                            + " WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", employeeIds))
                    .stream()
                    .collect(Collectors.toMap(
                            row -> UUID.fromString((String) row.get("id")),
                            row -> new EmpInfo(
                                    (String) row.get("first_name"),
                                    (String) row.get("last_name"),
                                    (String) row.get("department_name"))));
        }

        List<CalibrationBoardEntry> entries = cycleReviews.stream()
                .sorted((a, b) -> {
                    EmpInfo ea = empMap.get(a.getEmployeeId());
                    EmpInfo eb = empMap.get(b.getEmployeeId());
                    String deptA = ea != null && ea.department() != null ? ea.department() : "";
                    String deptB = eb != null && eb.department() != null ? eb.department() : "";
                    int cmp = deptA.compareToIgnoreCase(deptB);
                    if (cmp != 0) return cmp;
                    if (a.getManagerRating() == null && b.getManagerRating() == null) return 0;
                    if (a.getManagerRating() == null) return 1;
                    if (b.getManagerRating() == null) return -1;
                    return b.getManagerRating().compareTo(a.getManagerRating());
                })
                .map(r -> {
                    EmpInfo emp = empMap.get(r.getEmployeeId());
                    String fullName = emp != null
                            ? emp.firstName() + " " + emp.lastName()
                            : r.getEmployeeId().toString();
                    String dept = emp != null ? emp.department() : null;
                    return new CalibrationBoardEntry(
                            r.getId(),
                            r.getEmployeeId(),
                            fullName,
                            dept,
                            r.getManagerId(),
                            r.getSelfRating(),
                            r.getManagerRating(),
                            r.getFinalRating(),
                            r.getFinalBand(),
                            r.getRecommendation(),
                            r.getBonusPercent(),
                            r.getCalibrationNotes(),
                            r.getPotentialRating(),
                            r.getPotentialNotes(),
                            r.isCalibrationLocked());
                })
                .collect(Collectors.toList());

        Map<String, Long> distribution = buildDistribution(cycleReviews);

        // M121 — overlay targets + per-band actual/target/delta cells.
        java.util.Map<String, java.math.BigDecimal> targetMap = new java.util.LinkedHashMap<>();
        for (var t : targets.findByCycleId(cycleId)) {
            targetMap.put(t.getBand(), t.getTargetPercent());
        }
        java.util.Map<String, CalibrationBoardMath.BoardCell> cells =
                CalibrationBoardMath.buildBoardDistribution(distribution, targetMap, entries.size());

        return new CalibrationBoardResponse(
                cycle.getId(),
                cycle.getName(),
                entries.size(),
                distribution,
                cells,
                targetMap,
                entries);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Session lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public CalibrationSessionResponse startSession(UUID sessionId) {
        CalibrationSession session = findSession(sessionId);
        if (!STATUS_SCHEDULED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "Session can only be started from SCHEDULED state (current: " + session.getStatus() + ")");
        }
        session.setStatus(STATUS_IN_PROGRESS);
        return CalibrationSessionResponse.from(sessions.save(session));
    }

    @Transactional
    public CalibrationSessionResponse completeSession(UUID sessionId) {
        CalibrationSession session = findSession(sessionId);
        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new BadRequestException(
                    "Session can only be completed from IN_PROGRESS state (current: " + session.getStatus() + ")");
        }
        // M121 — lock every review touched by this session so a stale
        // edit can't quietly overwrite the calibration outcome. Uses a
        // single UPDATE to flip the flag on each distinct review the
        // edit log mentions, and stamps the count on the session for
        // the list-screen badge.
        int locked = jdbc.update(
                "UPDATE performance.performance_review "
                + "   SET calibration_locked = TRUE "
                + " WHERE id IN ("
                + "     SELECT DISTINCT review_id"
                + "       FROM performance.calibration_edit_log"
                + "      WHERE session_id = :sessionId)"
                + "   AND calibration_locked = FALSE",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("sessionId", sessionId));
        session.setStatus(STATUS_COMPLETED);
        session.setLockedAt(java.time.OffsetDateTime.now());
        session.setLockedReviews(locked);
        return CalibrationSessionResponse.from(sessions.save(session));
    }

    /**
     * M121 — HR-admin override to reopen the lock so a fix can be made
     * after the meeting. Doesn't change session status — sessions stay
     * COMPLETED — but flips the per-review flag back to false. The
     * unlock itself writes an edit-log row so HR can still see who
     * re-opened it.
     */
    @Transactional
    public void unlockReview(UUID reviewId) {
        PerformanceReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (!review.isCalibrationLocked()) return;
        java.util.Map<String, Object> before = java.util.Map.of("calibrationLocked", true);
        review.setCalibrationLocked(false);
        reviews.save(review);
        // Find the most recent session that touched this review so the
        // unlock attaches to the correct meeting in the edit log.
        java.util.List<az.millers.hcm.performance.domain.CalibrationEditLog> recent =
                editLogs.findByReviewIdOrderByEditedAtDesc(reviewId);
        UUID sessionId = recent.isEmpty() ? null : recent.get(0).getSessionId();
        if (sessionId != null) {
            az.millers.hcm.performance.domain.CalibrationEditLog log =
                    new az.millers.hcm.performance.domain.CalibrationEditLog();
            log.setSessionId(sessionId);
            log.setReviewId(reviewId);
            log.setEditedBy(currentRequest.username());
            log.setBeforeJson(before);
            log.setAfterJson(java.util.Map.of("calibrationLocked", false, "action", "UNLOCKED"));
            editLogs.save(log);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Calibrate a review within a session
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public PerformanceReview calibrateReview(UUID sessionId, UUID reviewId, CalibrationRequest req) {
        CalibrationSession session = findSession(sessionId);
        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new BadRequestException(
                    "Calibration is only allowed when the session is IN_PROGRESS (current: " + session.getStatus() + ")");
        }
        // Validate the review actually belongs to the same cycle as the session
        PerformanceReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (!review.getCycleId().equals(session.getCycleId())) {
            throw new BadRequestException(
                    "Review " + reviewId + " does not belong to cycle " + session.getCycleId());
        }
        // M121 — reject calibrating a locked review. HR can call
        // unlockReview to reopen it.
        if (review.isCalibrationLocked()) {
            throw new BadRequestException(
                    "Review " + reviewId + " is locked by a completed calibration session — unlock first to re-edit");
        }
        // M121 — snapshot the BEFORE state of every field calibrate can
        // touch, then call the underlying calibrate, then capture AFTER.
        // Done here rather than inside PerformanceReviewService because
        // the edit log is a calibration-session concept, not a review one.
        java.util.Map<String, Object> before = snapshotCalibrationFields(review);
        PerformanceReview after = reviewService.calibrate(reviewId, req);
        java.util.Map<String, Object> afterSnapshot = snapshotCalibrationFields(after);
        az.millers.hcm.performance.domain.CalibrationEditLog log =
                new az.millers.hcm.performance.domain.CalibrationEditLog();
        log.setSessionId(sessionId);
        log.setReviewId(reviewId);
        log.setEditedBy(currentRequest.username());
        log.setBeforeJson(before);
        log.setAfterJson(afterSnapshot);
        editLogs.save(log);
        return after;
    }

    /** Capture the calibrate-touched fields for the edit-log. Keep in sync with PerformanceReviewService.calibrate. */
    private static java.util.Map<String, Object> snapshotCalibrationFields(PerformanceReview r) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("managerRating", r.getManagerRating());
        m.put("finalRating", r.getFinalRating());
        m.put("finalBand", r.getFinalBand());
        m.put("recommendation", r.getRecommendation());
        m.put("bonusPercent", r.getBonusPercent());
        m.put("calibrationNotes", r.getCalibrationNotes());
        m.put("potentialRating", r.getPotentialRating());
        m.put("potentialNotes", r.getPotentialNotes());
        return m;
    }

    // ── M121 — edit log read surface ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<az.millers.hcm.performance.domain.CalibrationEditLog> editLogForSession(UUID sessionId) {
        findSession(sessionId); // 404 on unknown
        return editLogs.findBySessionIdOrderByEditedAtDesc(sessionId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────────────────

    private CalibrationSession findSession(UUID sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Calibration session not found: " + sessionId));
    }

    /**
     * Builds a band→count distribution map. Uses {@code final_band} when
     * present; for reviews without a band, derives a label from
     * {@code manager_rating} ranges (1–5 scale). Preserves insertion order
     * for a natural high-to-low display.
     */
    private Map<String, Long> buildDistribution(List<PerformanceReview> cycleReviews) {
        // Predefined order
        String[] orderedBands = {
            "5 - Exceptional",
            "4 - Exceeds",
            "3 - Meets",
            "2 - Needs Improvement",
            "1 - Unsatisfactory",
            "Unrated"
        };

        Map<String, Long> raw = cycleReviews.stream()
                .collect(Collectors.groupingBy(r -> {
                    if (r.getFinalBand() != null && !r.getFinalBand().isBlank()) {
                        return r.getFinalBand();
                    }
                    if (r.getManagerRating() != null) {
                        int rounded = r.getManagerRating().setScale(0, java.math.RoundingMode.HALF_UP).intValue();
                        return switch (rounded) {
                            case 5  -> "5 - Exceptional";
                            case 4  -> "4 - Exceeds";
                            case 3  -> "3 - Meets";
                            case 2  -> "2 - Needs Improvement";
                            case 1  -> "1 - Unsatisfactory";
                            default -> "Unrated";
                        };
                    }
                    return "Unrated";
                }, Collectors.counting()));

        // Return in defined order; omit zero-count bands (except if all zero)
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String band : orderedBands) {
            long count = raw.getOrDefault(band, 0L);
            if (count > 0) ordered.put(band, count);
        }
        // Add any unexpected band keys that weren't in the predefined list
        raw.forEach((k, v) -> ordered.putIfAbsent(k, v));
        return ordered;
    }
}
