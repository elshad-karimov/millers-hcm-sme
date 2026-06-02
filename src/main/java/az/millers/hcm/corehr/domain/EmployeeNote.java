package az.millers.hcm.corehr.domain;

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
 * Free-text HR / manager note about an employee (M72 / P2-10).
 *
 * <p>{@link #visibilityLevel} drives the application-side filter — confidential
 * notes can be invisible even to other HR specialists. The DB only pins the
 * column to the enum string set; {@link EmployeeNoteService} does the per-row
 * filter via {@link NoteVisibility#isVisibleToCurrentCaller()}.
 */
@Entity
@Table(name = "employee_note", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeNote {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType noteType = NoteType.GENERAL;

    @Column(name = "note_body", nullable = false, columnDefinition = "text")
    private String noteBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_level", nullable = false, length = 30)
    private NoteVisibility visibilityLevel = NoteVisibility.ALL_HR;

    @Column(nullable = false)
    private boolean pinned = false;

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
