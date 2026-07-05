package az.millers.hcm.ehs.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "incident_witness", schema = "ehs")
@Data
public class IncidentWitness {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact", length = 200)
    private String contact;

    @Column(name = "statement", length = 2000)
    private String statement;
}
