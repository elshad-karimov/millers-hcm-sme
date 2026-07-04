package az.millers.hcm.recruitment.domain;

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

@Entity
@Table(name = "application_event", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationEvent {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage")
    private ApplicationStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage")
    private ApplicationStage toStage;

    private Integer rating;

    @Enumerated(EnumType.STRING)
    private Recommendation recommendation;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
