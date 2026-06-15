package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.Agency;
import az.millers.hcm.recruitment.domain.AgencyFeeType;
import az.millers.hcm.recruitment.domain.AgencyInvoice;
import az.millers.hcm.recruitment.domain.AgencySubmission;
import az.millers.hcm.recruitment.domain.InvoiceStatus;
import az.millers.hcm.recruitment.domain.SubmissionStatus;

/** M296 — Recruitment PRD Phase F agency master / submission / invoice DTOs. */
public final class AgencyDtos {

    private AgencyDtos() {}

    // ── Agency master ──────────────────────────────────────────────────

    public record AgencyRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 160) String contactName,
            @Size(max = 160) String contactEmail,
            @Size(max = 40) String contactPhone,
            @NotNull AgencyFeeType feeType,
            BigDecimal feePercent,
            BigDecimal feeFixed,
            Integer ownershipDays,
            @Size(max = 3) String currency,
            Boolean active,
            @Size(max = 1000) String notes) {}

    public record AgencyResponse(
            UUID id, String agencyNo, String name,
            String contactName, String contactEmail, String contactPhone,
            AgencyFeeType feeType, BigDecimal feePercent, BigDecimal feeFixed,
            int ownershipDays, String currency, boolean active, String notes) {

        public static AgencyResponse from(Agency a) {
            return new AgencyResponse(a.getId(), a.getAgencyNo(), a.getName(),
                    a.getContactName(), a.getContactEmail(), a.getContactPhone(),
                    a.getFeeType(), a.getFeePercent(), a.getFeeFixed(),
                    a.getOwnershipDays(), a.getCurrency(), a.isActive(), a.getNotes());
        }
    }

    // ── Submission ─────────────────────────────────────────────────────

    public record SubmissionRequest(
            @NotNull UUID agencyId,
            @NotNull UUID candidateId,
            UUID vacancyId,
            @Size(max = 1000) String notes) {}

    public record SubmissionResponse(
            UUID id, String submissionNo,
            UUID agencyId, String agencyName,
            UUID candidateId, String candidateName,
            UUID vacancyId, String vacancyTitle,
            SubmissionStatus status,
            OffsetDateTime submittedAt, OffsetDateTime ownershipUntil,
            UUID hireEmployeeId, String notes) {

        public static SubmissionResponse from(AgencySubmission s, String agencyName,
                                               String candidateName, String vacancyTitle) {
            return new SubmissionResponse(s.getId(), s.getSubmissionNo(),
                    s.getAgencyId(), agencyName, s.getCandidateId(), candidateName,
                    s.getVacancyId(), vacancyTitle, s.getStatus(),
                    s.getSubmittedAt(), s.getOwnershipUntil(), s.getHireEmployeeId(), s.getNotes());
        }
    }

    // ── Invoice ────────────────────────────────────────────────────────

    /** Edit a DRAFT invoice's amount / notes and/or transition its status. */
    public record InvoiceUpdate(
            InvoiceStatus status,
            BigDecimal feeAmount,
            @Size(max = 1000) String notes) {}

    public record InvoiceResponse(
            UUID id, String invoiceNo,
            UUID agencyId, String agencyName,
            UUID submissionId,
            UUID candidateId, String candidateName,
            UUID hireEmployeeId,
            BigDecimal feeAmount, String currency,
            InvoiceStatus status,
            OffsetDateTime issuedAt, OffsetDateTime sentAt, OffsetDateTime paidAt,
            String notes) {

        public static InvoiceResponse from(AgencyInvoice i, String agencyName,
                                            String candidateName) {
            return new InvoiceResponse(i.getId(), i.getInvoiceNo(),
                    i.getAgencyId(), agencyName, i.getSubmissionId(),
                    i.getCandidateId(), candidateName, i.getHireEmployeeId(),
                    i.getFeeAmount(), i.getCurrency(), i.getStatus(),
                    i.getIssuedAt(), i.getSentAt(), i.getPaidAt(), i.getNotes());
        }
    }
}
