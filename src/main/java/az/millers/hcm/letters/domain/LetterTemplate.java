package az.millers.hcm.letters.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * Reusable HR letter template (M77 / P2-16). {@code body} carries
 * {{placeholder}} markers; {@link #placeholdersJson} declares the
 * extra placeholders the requester must populate (the employee /
 * today context is auto-derived by the service).
 */
@Entity
@Table(name = "letter_template", schema = "hr_letters")
@Getter
@Setter
@NoArgsConstructor
public class LetterTemplate {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /**
     * M77 left this UNIQUE-by-code. M139 splits the constraint: same
     * code is allowed across languages (uq_letter_template_code_lang
     * enforces that pair).
     */
    @Column(nullable = false, length = 40)
    private String code;

    /**
     * M139 — ISO 639-1 alpha-2 (lowercase). Render path picks the
     * variant matching the employee's {@code native_language} (M132),
     * falling back to {@code 'en'} when no match exists.
     */
    @Column(nullable = false, length = 2)
    private String language = "en";

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "placeholders_json", columnDefinition = "jsonb")
    private JsonNode placeholdersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 20)
    private LetterOutputFormat outputFormat = LetterOutputFormat.TEXT;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
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
