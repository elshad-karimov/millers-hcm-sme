package az.millers.hcm.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignmentResponse;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.BulkAssignRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.BulkAssignResult;
import az.millers.hcm.learning.domain.PathAssignmentStatus;

/**
 * Pins the bulk-assign outcome-counting logic (M101).
 *
 * <p>Mockito-free — {@link LearningPathAssignmentService} can't be mocked
 * on Java 25 (Byte Buddy doesn't support the class file version yet).
 * Instead we create anonymous subclasses that override {@code assign()} with
 * a controlled lambda — the same reflection-free pattern used in M68/M88/M92.
 */
class BulkAssignServiceTest {

    /** Minimal AssignmentResponse fixture for a given employee. */
    private static AssignmentResponse fakeResponse(UUID employeeId) {
        return new AssignmentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Path", employeeId, "Emp",
                PathAssignmentStatus.ASSIGNED, null, "hr", null,
                null, null, null, null,
                0, 0, 0, 0, 0, List.of());
    }

    /**
     * Returns a LearningPathAssignmentService whose assign() delegates to
     * {@code fn}. All other methods throw UnsupportedOperationException so
     * they can't be called accidentally.
     */
    private static LearningPathAssignmentService withAssign(
            Function<AssignRequest, AssignmentResponse> fn) {

        return new LearningPathAssignmentService(
                null, null, null, null, null, null, null, null, null) {

            @Override
            public AssignmentResponse assign(UUID pathId, AssignRequest req) {
                return fn.apply(req);
            }
        };
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void allSucceed() {
        UUID p = UUID.randomUUID();
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();
        LearningPathAssignmentService svc = withAssign(req -> fakeResponse(req.employeeId()));

        BulkAssignResult r = svc.bulkAssign(p,
                new BulkAssignRequest(List.of(e1, e2), null, null));

        assertThat(r.requested()).isEqualTo(2);
        assertThat(r.succeeded()).isEqualTo(2);
        assertThat(r.skipped()).isZero();
        assertThat(r.failed()).isZero();
        assertThat(r.rows()).allMatch(row -> row.success());
    }

    @Test
    void badRequestExceptionCountsAsSkip() {
        UUID p = UUID.randomUUID();
        UUID good = UUID.randomUUID(), already = UUID.randomUUID();
        LearningPathAssignmentService svc = withAssign(req -> {
            if (req.employeeId().equals(already)) {
                throw new BadRequestException("Employee already has an active assignment for this path");
            }
            return fakeResponse(req.employeeId());
        });

        BulkAssignResult r = svc.bulkAssign(p,
                new BulkAssignRequest(List.of(good, already), null, null));

        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.failed()).isZero();
        assertThat(r.rows().stream()
                .filter(row -> !row.success())
                .findFirst().orElseThrow()
                .outcome()).contains("already has an active assignment");
    }

    @Test
    void unexpectedExceptionCountsAsFailed() {
        UUID p = UUID.randomUUID();
        UUID good = UUID.randomUUID(), broken = UUID.randomUUID();
        LearningPathAssignmentService svc = withAssign(req -> {
            if (req.employeeId().equals(broken)) {
                throw new RuntimeException("DB connection lost");
            }
            return fakeResponse(req.employeeId());
        });

        BulkAssignResult r = svc.bulkAssign(p,
                new BulkAssignRequest(List.of(good, broken), null, null));

        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.skipped()).isZero();
        assertThat(r.failed()).isEqualTo(1);
    }

    @Test
    void doesNotAbortAfterFailure() {
        // A failure on employee 1 must not abort employees 2 and 3.
        UUID p = UUID.randomUUID();
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID(), e3 = UUID.randomUUID();
        LearningPathAssignmentService svc = withAssign(req -> {
            if (req.employeeId().equals(e1)) throw new RuntimeException("transient");
            return fakeResponse(req.employeeId());
        });

        BulkAssignResult r = svc.bulkAssign(p,
                new BulkAssignRequest(List.of(e1, e2, e3), null, null));

        assertThat(r.succeeded()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.rows()).hasSize(3);
    }

    @Test
    void emptyListReturnsZeroResult() {
        // assign() must never be called.
        LearningPathAssignmentService svc = withAssign(req -> {
            throw new AssertionError("assign() called on empty list");
        });

        BulkAssignResult r = svc.bulkAssign(UUID.randomUUID(),
                new BulkAssignRequest(List.of(), null, null));

        assertThat(r.requested()).isZero();
        assertThat(r.succeeded()).isZero();
    }

    @Test
    void targetDateAndNotesForwardedToEachEmployee() {
        UUID p = UUID.randomUUID();
        LocalDate target = LocalDate.of(2026, 12, 31);
        List<AssignRequest> captured = new ArrayList<>();
        LearningPathAssignmentService svc = withAssign(req -> {
            captured.add(req);
            return fakeResponse(req.employeeId());
        });

        svc.bulkAssign(p, new BulkAssignRequest(
                List.of(UUID.randomUUID(), UUID.randomUUID()), target, "Team Q4 upskill"));

        assertThat(captured).allMatch(req ->
                target.equals(req.targetCompletionDate())
                        && "Team Q4 upskill".equals(req.notes()));
    }
}
