package az.millers.hcm.common;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Half-open date window used by analytics services (M90).
 *
 * <p>The {@link #fromTs()} bound is inclusive (start of {@code from} day in UTC)
 * and the {@link #toTsExclusive()} bound is exclusive (start of the day AFTER
 * {@code to} in UTC). This matches what SQL window queries actually want —
 * {@code created_at &gt;= fromTs AND created_at &lt; toTsExclusive} — and
 * avoids the off-by-one bug of ending at "to.atTime(23:59:59.999)".
 *
 * <p>Extracted from {@code RecruitmentAnalyticsService} where the same
 * UTC-midnight conversion was hand-rolled 3 times in one file. Any new
 * analytics surface should accept a {@code DateWindow} or build one with
 * {@link #ofOrDefault(LocalDate, LocalDate, java.time.Period)}.
 */
public record DateWindow(LocalDate from, LocalDate to,
                          OffsetDateTime fromTs, OffsetDateTime toTsExclusive) {

    /** Build a window for {@code [from, to]} (inclusive on both ends, day-precision). */
    public static DateWindow of(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("DateWindow.of requires non-null from + to");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("DateWindow: from > to (" + from + " > " + to + ")");
        }
        OffsetDateTime fromTs = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toTsExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        return new DateWindow(from, to, fromTs, toTsExclusive);
    }

    /**
     * Build a window applying defaults when the caller passes nulls.
     * Missing {@code from} → {@code today - defaultBack}. Missing {@code to}
     * → {@code today}. The defaultBack value carries a {@link java.time.Period}
     * so callers can read {@code Period.ofYears(1)} at the call site.
     */
    public static DateWindow ofOrDefault(LocalDate from, LocalDate to,
                                          java.time.Period defaultBack) {
        LocalDate today = LocalDate.now();
        LocalDate resolvedTo = to == null ? today : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minus(defaultBack) : from;
        return of(resolvedFrom, resolvedTo);
    }

    /** @return true iff {@code ts} ∈ [fromTs, toTsExclusive). */
    public boolean contains(OffsetDateTime ts) {
        return ts != null && !ts.isBefore(fromTs) && ts.isBefore(toTsExclusive);
    }
}
