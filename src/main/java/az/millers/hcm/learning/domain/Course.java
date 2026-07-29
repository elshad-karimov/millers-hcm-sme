package az.millers.hcm.learning.domain;

import java.math.BigDecimal;
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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "course", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "course_no", nullable = false, unique = true)
    private String courseNo;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "content_markdown", columnDefinition = "text")
    private String contentMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourseCategory category;

    @Column(name = "duration_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal durationHours = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "passing_score", nullable = false)
    private int passingScore = 70;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourseStatus status;

    @Column(name = "instructor_id")
    private UUID instructorId;

    @Column(name = "valid_for_months")
    private Integer validForMonths;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = CourseStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
