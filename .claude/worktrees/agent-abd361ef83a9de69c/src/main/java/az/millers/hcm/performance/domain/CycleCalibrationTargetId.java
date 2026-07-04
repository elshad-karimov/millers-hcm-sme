package az.millers.hcm.performance.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * M121 — composite key for {@link CycleCalibrationTarget}: (cycle, band).
 */
public class CycleCalibrationTargetId implements Serializable {

    private UUID cycleId;
    private String band;

    public CycleCalibrationTargetId() {}

    public CycleCalibrationTargetId(UUID cycleId, String band) {
        this.cycleId = cycleId;
        this.band = band;
    }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CycleCalibrationTargetId other)) return false;
        return Objects.equals(cycleId, other.cycleId) && Objects.equals(band, other.band);
    }

    @Override
    public int hashCode() { return Objects.hash(cycleId, band); }
}
