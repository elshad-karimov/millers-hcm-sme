package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.api.dto.CalibrationBoardResponse;
import az.millers.hcm.performance.api.dto.CalibrationRequest;
import az.millers.hcm.performance.api.dto.CalibrationSessionRequest;
import az.millers.hcm.performance.api.dto.CalibrationSessionResponse;
import az.millers.hcm.performance.api.dto.PerformanceReviewResponse;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.service.CalibrationSessionService;
import jakarta.validation.Valid;

/**
 * Calibration session endpoints (M56 – PRD §8.13).
 *
 * <pre>
 * POST   /api/performance/cycles/{cycleId}/calibration-sessions         201
 * GET    /api/performance/cycles/{cycleId}/calibration-sessions         200
 * GET    /api/performance/cycles/{cycleId}/calibration-board            200
 * POST   /api/performance/calibration-sessions/{sessionId}/start        200
 * POST   /api/performance/calibration-sessions/{sessionId}/reviews/{reviewId}/calibrate  200
 * POST   /api/performance/calibration-sessions/{sessionId}/complete     200
 * </pre>
 */
@RestController
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
public class CalibrationSessionController {

    private final CalibrationSessionService service;

    public CalibrationSessionController(CalibrationSessionService service) {
        this.service = service;
    }

    // ── Cycle-scoped ────────────────────────────────────────────────────────

    @PostMapping("/api/performance/cycles/{cycleId}/calibration-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationSessionResponse create(
            @PathVariable UUID cycleId,
            @Valid @RequestBody CalibrationSessionRequest req) {
        return service.createSession(cycleId, req);
    }

    @GetMapping("/api/performance/cycles/{cycleId}/calibration-sessions")
    public List<CalibrationSessionResponse> list(@PathVariable UUID cycleId) {
        return service.listSessions(cycleId);
    }

    @GetMapping("/api/performance/cycles/{cycleId}/calibration-board")
    public CalibrationBoardResponse board(@PathVariable UUID cycleId) {
        return service.getBoard(cycleId);
    }

    // ── Session-scoped ──────────────────────────────────────────────────────

    @PostMapping("/api/performance/calibration-sessions/{sessionId}/start")
    public CalibrationSessionResponse start(@PathVariable UUID sessionId) {
        return service.startSession(sessionId);
    }

    @PostMapping("/api/performance/calibration-sessions/{sessionId}/reviews/{reviewId}/calibrate")
    public PerformanceReviewResponse calibrate(
            @PathVariable UUID sessionId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody CalibrationRequest req) {
        PerformanceReview review = service.calibrateReview(sessionId, reviewId, req);
        return PerformanceReviewResponse.from(review);
    }

    @PostMapping("/api/performance/calibration-sessions/{sessionId}/complete")
    public CalibrationSessionResponse complete(@PathVariable UUID sessionId) {
        return service.completeSession(sessionId);
    }
}
