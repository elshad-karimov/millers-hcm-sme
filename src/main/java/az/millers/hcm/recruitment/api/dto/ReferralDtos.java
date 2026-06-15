package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.Referral;
import az.millers.hcm.recruitment.domain.ReferralStatus;

/** M295 — Recruitment PRD Phase F referral-program DTOs. */
public final class ReferralDtos {

    private ReferralDtos() {}

    /** Log a new referral. Bonus / qualifying days default from config when null. */
    public record ReferralRequest(
            @NotNull UUID referrerEmployeeId,
            @NotNull UUID candidateId,
            UUID vacancyId,
            @DecimalMin("0.0") BigDecimal bonusAmount,
            Integer qualifyingDays,
            @Size(max = 3) String currency,
            @Size(max = 1000) String notes) {}

    public record ReferralResponse(
            UUID id,
            String referralNo,
            UUID referrerEmployeeId,
            String referrerName,
            UUID candidateId,
            String candidateName,
            UUID vacancyId,
            String vacancyTitle,
            ReferralStatus status,
            BigDecimal bonusAmount,
            String currency,
            int qualifyingDays,
            OffsetDateTime hiredAt,
            OffsetDateTime qualifiedAt,
            OffsetDateTime paidAt,
            UUID payrollRunId,
            UUID payrollBonusId,
            String notes,
            OffsetDateTime createdAt) {

        public static ReferralResponse from(Referral r, String referrerName,
                                             String candidateName, String vacancyTitle) {
            return new ReferralResponse(r.getId(), r.getReferralNo(),
                    r.getReferrerEmployeeId(), referrerName,
                    r.getCandidateId(), candidateName,
                    r.getVacancyId(), vacancyTitle,
                    r.getStatus(), r.getBonusAmount(), r.getCurrency(), r.getQualifyingDays(),
                    r.getHiredAt(), r.getQualifiedAt(), r.getPaidAt(),
                    r.getPayrollRunId(), r.getPayrollBonusId(), r.getNotes(), r.getCreatedAt());
        }
    }
}
