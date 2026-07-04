package az.millers.hcm.recruitment.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.CandidateDocument;
import az.millers.hcm.recruitment.domain.DocumentStatus;
import az.millers.hcm.recruitment.domain.DocumentType;

/** M292 — Recruitment PRD §12 document-checklist DTOs. */
public final class CandidateDocumentDtos {

    private CandidateDocumentDtos() {}

    // ── Document-type catalog ──────────────────────────────────────────

    public record TypeRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            boolean defaultRequired,
            int ordinal,
            Boolean active) {}

    public record TypeResponse(
            UUID id, String code, String name, String description,
            boolean defaultRequired, int ordinal, boolean active) {

        public static TypeResponse from(DocumentType t) {
            return new TypeResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(),
                    t.isDefaultRequired(), t.getOrdinal(), t.isActive());
        }
    }

    // ── Per-candidate checklist ────────────────────────────────────────

    /** Add one document type to a candidate's checklist. */
    public record AddItemRequest(
            @NotNull UUID documentTypeId,
            Boolean required) {}

    /** Move an item along its lifecycle. */
    public record StatusUpdate(
            @NotNull DocumentStatus status,
            @Size(max = 500) String notes,
            Boolean required) {}

    public record ItemResponse(
            UUID id,
            UUID documentTypeId,
            String typeCode,
            String typeName,
            boolean required,
            DocumentStatus status,
            String notes,
            OffsetDateTime receivedAt,
            OffsetDateTime verifiedAt,
            String verifiedBy) {

        public static ItemResponse from(CandidateDocument d, DocumentType t) {
            return new ItemResponse(d.getId(), d.getDocumentTypeId(),
                    t == null ? "?" : t.getCode(), t == null ? "?" : t.getName(),
                    d.isRequired(), d.getStatus(), d.getNotes(),
                    d.getReceivedAt(), d.getVerifiedAt(), d.getVerifiedBy());
        }
    }

    /** The whole checklist plus completion counters. */
    public record Checklist(
            List<ItemResponse> items,
            int total,
            int requiredCount,
            int verifiedCount,
            int outstandingRequired) {}
}
