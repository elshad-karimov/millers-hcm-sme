package az.millers.hcm.learning.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/** HCM_14 M404 — classroom / virtual venue with capacity. */
@Entity
@Table(name = "training_room", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingRoom {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String location;

    @Column
    private Integer capacity;

    /** Virtual room (video-call link in location). */
    @Column(nullable = false)
    private boolean virtual = false;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
