package az.millers.hcm.staffing.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * Position control caps hiring at a position's approved headcount. This
 * edition does not practise it: nobody maintains those numbers, so every
 * position sat at the default of 1 and the second hire into any position was
 * refused — which reads as a broken system, not a policy.
 *
 * <p>The cap is therefore off by default and switchable, and both directions
 * are pinned here so neither can drift silently.
 */
class PositionHeadcountGateTest {

    private final PositionRepository positions = mock(PositionRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final VacancyRepository vacancies = mock(VacancyRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PositionFundingService funding = mock(PositionFundingService.class);

    private final UUID positionId = UUID.randomUUID();

    @Test
    @DisplayName("a full position still accepts a hire when the cap is off")
    void capOffAllowsHiringPastApprovedHeadcount() {
        givenPositionAtCapacity();
        PositionHeadcountService gate = service(false);

        assertThatCode(() -> gate.assertCanFill(positionId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("with the cap on, the position refuses the hire")
    void capOnStillBlocks() {
        givenPositionAtCapacity();
        PositionHeadcountService gate = service(true);

        assertThatThrownBy(() -> gate.assertCanFill(positionId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at capacity");
    }

    @Test
    @DisplayName("the cap being off does not open closed positions")
    void statusIsStillEnforced() {
        Position p = position();
        p.setStatus(PositionStatus.CLOSED);
        when(positions.findById(positionId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service(false).assertCanFill(positionId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CLOSED");
    }

    private void givenPositionAtCapacity() {
        when(positions.findById(positionId)).thenReturn(Optional.of(position()));
        // One approved seat, one occupied — the exact case the screen hit.
        when(employees.countActiveByPositionId(any())).thenReturn(1L);
    }

    private Position position() {
        Position p = new Position();
        p.setCode("POS-00001");
        p.setStatus(PositionStatus.ACTIVE);
        p.setApprovedHeadcount(1);
        return p;
    }

    // ─── The second cap ────────────────────────────────────────────────────
    // The gate above decides whether a hire is allowed; StaffingService then
    // decides whether the resulting count may be written down. Leaving one on
    // while turning the other off produced exactly what happened in testing:
    // the hire passed the gate and failed on the counter, with a different
    // message. Both directions are pinned so they cannot drift apart again.

    @Test
    @DisplayName("the occupied count may pass approved when the cap is off")
    void occupancyMayExceedApprovedWhenCapOff() {
        assertThatCode(() -> staffing(false).adjustOccupancy(positionId, 1, "hire"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("with the cap on, the count refuses to go past approved")
    void occupancyStillCappedWhenOn() {
        assertThatThrownBy(() -> staffing(true).adjustOccupancy(positionId, 1, "hire"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("would exceed approved headcount");
    }

    @Test
    @DisplayName("a negative count is refused either way — it is never meaningful")
    void occupancyNeverGoesNegative() {
        assertThatThrownBy(() -> staffing(false).adjustOccupancy(positionId, -2, "exit"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("below zero");
    }

    private StaffingService staffing(boolean enforce) {
        Position p = position();
        p.setOccupiedHeadcount(1);   // already full: approved 1, occupied 1
        when(positions.findById(positionId)).thenReturn(Optional.of(p));
        when(positions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CurrentRequest currentRequest = mock(CurrentRequest.class);
        when(currentRequest.username()).thenReturn("admin");
        return new StaffingService(positions, audit, currentRequest,
                mock(PositionLifecycleService.class), enforce);
    }

    private PositionHeadcountService service(boolean enforce) {
        return new PositionHeadcountService(positions, employees, vacancies, audit, funding, enforce);
    }
}
