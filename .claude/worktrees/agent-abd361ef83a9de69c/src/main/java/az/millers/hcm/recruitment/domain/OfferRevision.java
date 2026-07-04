package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M284 — Recruitment PRD §33: a snapshot of an offer's terms BEFORE
 * a revision was applied. Together the rows form the negotiation
 * history; "previous offer comparison" is a plain read.
 */
@Entity
@Table(name = "offer_revision", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class OfferRevision {

    public enum Reason { CANDIDATE_COUNTER, HR_REVISION }

    @Id
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "prev_salary", precision = 12, scale = 2)
    private BigDecimal prevSalary;

    @Column(name = "prev_currency", length = 3)
    private String prevCurrency;

    @Column(name = "prev_start_date")
    private LocalDate prevStartDate;

    @Column(name = "prev_benefits", columnDefinition = "text")
    private String prevBenefits;

    @Column(name = "prev_status", nullable = false, length = 20)
    private String prevStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
