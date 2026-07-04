package az.millers.hcm.recruitment.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M278 — Recruitment PRD §8: a channel- and language-specific
 * advertisement of an approved requisition. One {@link Vacancy} can
 * have many postings (career portal AZ + EN, internal, job board, …).
 */
@Entity
@Table(name = "job_posting", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class JobPosting {

    public enum Channel { INTERNAL, EXTERNAL, JOB_BOARD, AGENCY, SOCIAL }

    public enum Status {
        DRAFT, PUBLISHED, PAUSED, EXPIRED, CLOSED;

        /** Visible on the career portal / internal job board. */
        public boolean isLive() {
            return this == PUBLISHED;
        }
    }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "posting_no", nullable = false, unique = true)
    private String postingNo;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel = Channel.EXTERNAL;

    /** ISO 639-1 — az / en / ru / tr; AZ first-class per the PRD. */
    @Column(nullable = false, length = 5)
    private String language = "az";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requirements;

    @Column(name = "benefits_description", columnDefinition = "text")
    private String benefitsDescription;

    /** PRD §8 — whether the salary range is shown publicly. */
    @Column(name = "salary_visible", nullable = false)
    private boolean salaryVisible;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "published_by", length = 120)
    private String publishedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
