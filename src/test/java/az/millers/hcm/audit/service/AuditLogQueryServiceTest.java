package az.millers.hcm.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.service.AuditLogQueryService.Filter;
import az.millers.hcm.common.BadRequestException;

/**
 * Pure-math pinning for the M114 audit-log query layer.
 *
 * <p>The two functions worth pinning are the filter normaliser (turns the
 * UI's blank-string inputs into nullable SQL params) and the paging guard
 * (refuses page=-1 / size=0 / size=999_999). Mis-implementing either lets a
 * caller stream the whole audit log into memory or matches rows whose
 * column equals the empty string instead of being unfiltered.
 */
class AuditLogQueryServiceTest {

    // ── Filter.normalise() ──────────────────────────────────────────────

    @Test
    void normaliseNullFilterIsAllNulls() {
        Filter out = Filter.normalise(null);
        assertThat(out.from()).isNull();
        assertThat(out.to()).isNull();
        assertThat(out.module()).isNull();
        assertThat(out.entityName()).isNull();
        assertThat(out.entityId()).isNull();
        assertThat(out.action()).isNull();
        assertThat(out.actor()).isNull();
    }

    @Test
    void normaliseBlankStringsBecomeNull() {
        Filter in = new Filter(null, null, "", " ", "\t", "  ", "");
        Filter out = Filter.normalise(in);
        assertThat(out.module()).isNull();
        assertThat(out.entityName()).isNull();
        assertThat(out.entityId()).isNull();
        assertThat(out.action()).isNull();
        assertThat(out.actor()).isNull();
    }

    @Test
    void normaliseTrimsButPreservesNonEmpty() {
        // Record arg order: from, to, module, entityName, entityId, action, actor.
        Filter in = new Filter(null, null,
                "  CORE_HR  ", "Employee", "  EMP-001  ",
                " UPDATE ", "  alice  ");
        Filter out = Filter.normalise(in);
        assertThat(out.module()).isEqualTo("CORE_HR");
        assertThat(out.entityName()).isEqualTo("Employee");
        assertThat(out.entityId()).isEqualTo("EMP-001");
        assertThat(out.action()).isEqualTo("UPDATE");
        assertThat(out.actor()).isEqualTo("alice");
    }

    @Test
    void normalisePreservesDateRange() {
        OffsetDateTime from = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 1, 31, 0, 0, 0, 0, ZoneOffset.UTC);
        Filter out = Filter.normalise(new Filter(from, to, null, null, null, null, null));
        assertThat(out.from()).isEqualTo(from);
        assertThat(out.to()).isEqualTo(to);
    }

    // ── validatePaging() ────────────────────────────────────────────────

    @Test
    void pagingAcceptsCanonicalRequest() {
        assertThatNoException().isThrownBy(() -> AuditLogQueryService.validatePaging(0, 50));
    }

    @Test
    void pagingAcceptsMaxSize() {
        assertThatNoException().isThrownBy(() -> AuditLogQueryService.validatePaging(
                0, AuditLogQueryService.MAX_PAGE_SIZE));
    }

    @Test
    void pagingRejectsNegativePage() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> AuditLogQueryService.validatePaging(-1, 50))
                .withMessageContaining("page must be >= 0");
    }

    @Test
    void pagingRejectsZeroSize() {
        // size=0 is a footgun — would loop forever if the caller paginates
        // until a partial page returns.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> AuditLogQueryService.validatePaging(0, 0))
                .withMessageContaining("size must be >= 1");
    }

    @Test
    void pagingRejectsNegativeSize() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> AuditLogQueryService.validatePaging(0, -10));
    }

    @Test
    void pagingRejectsOversizedPage() {
        // Without a cap a caller can ask for size=1M and OOM the JVM.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> AuditLogQueryService.validatePaging(
                        0, AuditLogQueryService.MAX_PAGE_SIZE + 1))
                .withMessageContaining("size must be <= ");
    }
}
