package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.CompCycle;
import az.millers.hcm.compbenefits.domain.CompCycleStatus;
import az.millers.hcm.compbenefits.domain.CompProposal;
import az.millers.hcm.compbenefits.domain.CompProposalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for the M118 compensation planning workbench. */
public final class CompPlanningDtos {

    private CompPlanningDtos() {}

    // ── Cycles ──────────────────────────────────────────────────────────

    public record CycleRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull LocalDate opensOn,
            @NotNull LocalDate closesOn,
            @PositiveOrZero BigDecimal poolTotal,
            String currency) {
    }

    public record CycleResponse(
            UUID id,
            String code,
            String name,
            String description,
            CompCycleStatus status,
            LocalDate opensOn,
            LocalDate closesOn,
            BigDecimal poolTotal,
            BigDecimal poolCommitted,
            BigDecimal poolRemaining,
            String currency,
            int proposalCount,
            OffsetDateTime createdAt) {

        public static CycleResponse from(CompCycle c,
                                          BigDecimal committed,
                                          int proposalCount) {
            BigDecimal remaining = c.getPoolTotal().subtract(committed);
            return new CycleResponse(
                    c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getStatus(), c.getOpensOn(), c.getClosesOn(),
                    c.getPoolTotal(), committed, remaining,
                    c.getCurrency(), proposalCount, c.getCreatedAt());
        }
    }

    // ── Proposals ───────────────────────────────────────────────────────

    public record ProposalRequest(
            @NotNull UUID cycleId,
            @NotNull UUID employeeId,
            @NotNull @PositiveOrZero BigDecimal proposedSalary,
            String rationale,
            LocalDate effectiveOn) {
    }

    public record DecisionRequest(
            @NotNull CompProposalStatus decision,
            String note,
            LocalDate effectiveOn) {
    }

    public record ProposalResponse(
            UUID id,
            UUID cycleId,
            UUID employeeId,
            String employeeName,
            String employeeNo,
            BigDecimal currentSalary,
            BigDecimal proposedSalary,
            BigDecimal deltaAmount,
            Double increasePercent,
            String currency,
            String rationale,
            CompProposalStatus status,
            String proposedBy,
            OffsetDateTime proposedAt,
            String decidedBy,
            OffsetDateTime decidedAt,
            String decisionNote,
            LocalDate effectiveOn) {

        public static ProposalResponse from(CompProposal p,
                                              String employeeName,
                                              String employeeNo,
                                              Double increasePercent) {
            BigDecimal delta = p.getProposedSalary().subtract(p.getCurrentSalary());
            return new ProposalResponse(
                    p.getId(), p.getCycleId(),
                    p.getEmployeeId(), employeeName, employeeNo,
                    p.getCurrentSalary(), p.getProposedSalary(), delta, increasePercent,
                    p.getCurrency(), p.getRationale(), p.getStatus(),
                    p.getProposedBy(), p.getProposedAt(),
                    p.getDecidedBy(), p.getDecidedAt(), p.getDecisionNote(),
                    p.getEffectiveOn());
        }
    }

    /** Per-cycle team grid returned to a manager. */
    public record TeamGrid(
            UUID cycleId,
            String cycleName,
            CompCycleStatus cycleStatus,
            BigDecimal poolTotal,
            BigDecimal poolCommitted,
            BigDecimal poolRemaining,
            String currency,
            List<TeamRow> rows) {

        /**
         * One row per direct report. {@code currentSalary} is loaded from the
         * latest active EmployeeCompensation. {@code proposal} is null when
         * the manager hasn't proposed anything for this employee yet.
         */
        public record TeamRow(
                UUID employeeId,
                String employeeNo,
                String employeeName,
                String orgUnitLabel,
                BigDecimal currentSalary,
                String currency,
                ProposalResponse proposal) {
        }
    }
}
