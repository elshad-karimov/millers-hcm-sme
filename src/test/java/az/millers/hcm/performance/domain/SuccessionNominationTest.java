package az.millers.hcm.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;

/**
 * Pins the isActive() helper and the V70 readiness-tier CHECK constraint
 * via the enum shape (M103).
 */
class SuccessionNominationTest {

    @Test
    void activeWhenNotCancelled() {
        SuccessionNomination n = new SuccessionNomination();
        assertThat(n.isActive()).isTrue();
    }

    @Test
    void inactiveWhenCancelled() {
        SuccessionNomination n = new SuccessionNomination();
        n.setCancelledAt(OffsetDateTime.now());
        assertThat(n.isActive()).isFalse();
    }

    @Test
    void readinessTierValuesMatchV70CheckConstraint() {
        // V70 CHECK: readiness_tier IN ('READY_NOW','READY_SOON',
        //   'READY_LONG_TERM','UNDER_DEVELOPMENT').
        // These are the same values used in SuccessionPlanService.readinessFor()
        // — any divergence would silently break the nomination → grid cross-link.
        assertThat(Readiness.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "READY_NOW", "READY_SOON", "READY_LONG_TERM", "UNDER_DEVELOPMENT");
    }
}
