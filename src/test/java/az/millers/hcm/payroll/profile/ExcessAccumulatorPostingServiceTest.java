package az.millers.hcm.payroll.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHoursRepository;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.event.TimesheetPeriodLockedEvent;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * The wiring that finally gives the accumulator a production caller.
 *
 * <p>Until this existed the ledger was a data model nothing filled. What matters
 * here is not that it sums hours — {@link ExcessAccumulatorServiceTest} already
 * pins that — but that it posts the right people, skips the right people, and
 * cannot take an attendance period down with it.
 */
class ExcessAccumulatorPostingServiceTest {

    private static final UUID ROTATION_EMP = UUID.randomUUID();
    private static final UUID ONSHORE_EMP = UUID.randomUUID();
    private static final UUID UNASSIGNED_EMP = UUID.randomUUID();

    private final List<ExcessAccumulator> accRows = new ArrayList<>();
    private final List<ExcessAccumulatorMonth> monthRows = new ArrayList<>();
    private final List<Timesheet> timesheetRows = new ArrayList<>();
    private final List<TimesheetMonthTotal> totalRows = new ArrayList<>();

    private PeriodNormHoursRepository normHours;
    private ExcessAccumulatorPostingService posting;
    private RecordingAudit audit;

    /**
     * This JDK cannot mock concrete classes, so the audit trail is captured by
     * subclassing rather than by Mockito — the same approach the timesheet
     * tests already take.
     */
    static final class RecordingAudit extends AuditService {
        final List<String> actions = new ArrayList<>();

        RecordingAudit() {
            super(null, null, null);
        }

        @Override
        public void record(String module, String entityName, String entityId,
                           String action, Object oldValue, Object newValue) {
            actions.add(action);
        }
    }

    @BeforeEach
    void setUp() {
        accRows.clear();
        monthRows.clear();
        timesheetRows.clear();
        totalRows.clear();

        TimesheetRepository timesheets = mock(TimesheetRepository.class);
        TimesheetMonthTotalRepository monthTotals = mock(TimesheetMonthTotalRepository.class);
        normHours = mock(PeriodNormHoursRepository.class);
        EmployeeCalculationProfileRepository assignments =
                mock(EmployeeCalculationProfileRepository.class);
        CalculationProfileRepository profiles = mock(CalculationProfileRepository.class);
        ExcessAccumulatorRepository accumulators = mock(ExcessAccumulatorRepository.class);
        ExcessAccumulatorMonthRepository months = mock(ExcessAccumulatorMonthRepository.class);
        BalancingPeriodDefRepository periodDefs = mock(BalancingPeriodDefRepository.class);
        audit = new RecordingAudit();

        when(timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(anyInt(), anyInt()))
                .thenAnswer(inv -> timesheetRows.stream()
                        .filter(t -> t.getPeriodYear() == (int) inv.getArgument(0)
                                && t.getPeriodMonth() == (int) inv.getArgument(1))
                        .toList());
        when(monthTotals.findByTimesheetIdOrderByCategoryCodeAsc(any()))
                .thenAnswer(inv -> totalRows.stream()
                        .filter(t -> t.getTimesheetId().equals(inv.getArgument(0)))
                        .toList());
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.of(norm(new BigDecimal("184"))));

        when(assignments.findByEmployeeIdOrderByEffectiveFromDesc(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(ROTATION_EMP)) return List.of(assignment(id, "OFFSHORE_ROTATION"));
            if (id.equals(ONSHORE_EMP)) return List.of(assignment(id, "ONSHORE_FIXED"));
            return List.of();
        });
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotationProfile()));
        when(profiles.findByCode("ONSHORE_FIXED")).thenReturn(Optional.of(onshoreProfile()));

        when(accumulators.save(any())).thenAnswer(inv -> {
            ExcessAccumulator a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
                accRows.add(a);
            }
            return a;
        });
        when(accumulators.findByEmployeeIdAndPeriodYearAndPeriodSeq(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> accRows.stream()
                        .filter(a -> a.getEmployeeId().equals(inv.getArgument(0))
                                && a.getPeriodYear() == (int) inv.getArgument(1)
                                && a.getPeriodSeq() == (int) inv.getArgument(2))
                        .findFirst());
        when(months.save(any())).thenAnswer(inv -> {
            ExcessAccumulatorMonth m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
                monthRows.add(m);
            }
            return m;
        });
        when(months.findByAccumulatorIdAndPeriodYearAndPeriodMonth(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> monthRows.stream()
                        .filter(m -> m.getAccumulatorId().equals(inv.getArgument(0))
                                && m.getPeriodYear() == (int) inv.getArgument(1)
                                && m.getPeriodMonth() == (int) inv.getArgument(2))
                        .findFirst());
        when(months.findByAccumulatorIdOrderByPeriodYearAscPeriodMonthAsc(any()))
                .thenAnswer(inv -> monthRows.stream()
                        .filter(m -> m.getAccumulatorId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparingInt(ExcessAccumulatorMonth::getPeriodYear)
                                .thenComparingInt(ExcessAccumulatorMonth::getPeriodMonth))
                        .toList());
        when(periodDefs.findBySchemeCodeOrderByPeriodSeqAsc(anyString()))
                .thenReturn(List.of(def(1, 1, 4, 4), def(2, 5, 8, 8), def(3, 9, 12, 12)));

        ExcessAccumulatorService service =
                new ExcessAccumulatorService(accumulators, months, periodDefs);
        posting = new ExcessAccumulatorPostingService(timesheets, monthTotals, normHours,
                assignments, profiles, service, audit);
    }

    // ---- fixtures ----------------------------------------------------------

    private static PeriodNormHours norm(BigDecimal hours) {
        PeriodNormHours n = new PeriodNormHours();
        n.setNormHours(hours);
        return n;
    }

    private static EmployeeCalculationProfile assignment(UUID employeeId, String code) {
        EmployeeCalculationProfile a = new EmployeeCalculationProfile();
        a.setEmployeeId(employeeId);
        a.setProfileCode(code);
        a.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return a;
    }

    private static CalculationProfile rotationProfile() {
        CalculationProfile p = new CalculationProfile();
        p.setCode("OFFSHORE_ROTATION");
        p.setOffshoreSalaryMode(CalculationProfile.OFFSHORE_MONTHLY_BASE);
        p.setOffshoreMultiplier(new BigDecimal("1.75"));
        p.setExcessMethod(CalculationProfile.EXCESS_BALANCING_PERIOD);
        p.setBalancingSchemeCode(BalancingScheme.OFFSHORE_4_MONTH);
        p.setAccumulatorCategories("OFFSHORE_HOURS,ONSHORE_HOURS");   // as V328 seeds it
        return p;
    }

    private static CalculationProfile onshoreProfile() {
        CalculationProfile p = new CalculationProfile();
        p.setCode("ONSHORE_FIXED");
        p.setOffshoreSalaryMode(CalculationProfile.OFFSHORE_NONE);
        p.setExcessMethod(CalculationProfile.EXCESS_NONE);
        return p;
    }

    private static BalancingPeriodDef def(int seq, int start, int end, int settle) {
        BalancingPeriodDef d = new BalancingPeriodDef();
        d.setSchemeCode(BalancingScheme.OFFSHORE_4_MONTH);
        d.setPeriodSeq(seq);
        d.setStartMonth(start);
        d.setEndMonth(end);
        d.setSettlementMonth(settle);
        return d;
    }

    private UUID givenTimesheet(UUID employeeId, int year, int month, TimesheetStatus status) {
        Timesheet t = new Timesheet();
        t.setId(UUID.randomUUID());
        t.setEmployeeId(employeeId);
        t.setPeriodYear(year);
        t.setPeriodMonth(month);
        t.setStatus(status);
        timesheetRows.add(t);
        return t.getId();
    }

    private void givenTotal(UUID timesheetId, String category, String quantity) {
        TimesheetMonthTotal m = new TimesheetMonthTotal();
        m.setTimesheetId(timesheetId);
        m.setCategoryCode(category);
        m.setQuantity(new BigDecimal(quantity));
        totalRows.add(m);
    }

    // ---- who gets posted ---------------------------------------------------

    @Test
    @DisplayName("only employees on a balancing profile are accumulated")
    void postsOnlyBalancingProfiles() {
        UUID rot = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(rot, "OFFSHORE_HOURS", "220");
        UUID ons = givenTimesheet(ONSHORE_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ons, "ONSHORE_HOURS", "200");

        ExcessAccumulatorPostingService.PostingResult r = posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(r.posted()).isEqualTo(1);
        assertThat(r.rows()).singleElement()
                .satisfies(row -> assertThat(row.employeeId()).isEqualTo(ROTATION_EMP));
        // An onshore employee is not a problem, just not applicable.
        assertThat(r.problems()).isEmpty();
    }

    @Test
    @DisplayName("the posted delta is actual minus norm, from the configured categories only")
    void sumsConfiguredCategories() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "180");
        givenTotal(ts, "ONSHORE_HOURS", "40");
        givenTotal(ts, "QUAYSIDE_HOURS", "100");   // not in the configured set

        ExcessAccumulatorPostingService.PostingResult r = posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.actualHours()).isEqualByComparingTo("220");   // 180 + 40, not 320
            assertThat(row.deltaHours()).isEqualByComparingTo("36");     // 220 - 184
            assertThat(row.categoriesUsed()).isEqualTo("OFFSHORE_HOURS,ONSHORE_HOURS");
        });
    }

    @Test
    @DisplayName("the categories used are recorded on the row, so a reconfig cannot rewrite it")
    void recordsCategoriesForTraceability() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "200");
        posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(monthRows).singleElement().satisfies(m -> {
            assertThat(m.getCategoriesUsed()).isEqualTo("OFFSHORE_HOURS,ONSHORE_HOURS");
            assertThat(m.getSource()).isEqualTo("PERIOD_LOCK");
        });
    }

    @Test
    @DisplayName("night hours join the sum only once Q1 says they are extra hours")
    void nightHoursFollowQ1() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "180");
        givenTotal(ts, "OFFSHORE_NIGHT_HOURS", "24");

        // Q1 unanswered: night is treated as already inside the offshore figure.
        assertThat(posting.post(2026, 7, "PERIOD_LOCK").rows())
                .singleElement()
                .satisfies(row -> assertThat(row.actualHours()).isEqualByComparingTo("180"));
    }

    @Test
    @DisplayName("a draft month is not accumulated — payroll consumes locked time only")
    void ignoresDraftMonths() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.DRAFT);
        givenTotal(ts, "OFFSHORE_HOURS", "220");

        assertThat(posting.post(2026, 7, "PERIOD_LOCK").posted()).isZero();
    }

    // ---- failures are contained ---------------------------------------------

    @Test
    @DisplayName("a missing norm is reported per employee, not thrown for the period")
    void missingNormIsPerEmployee() {
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "220");

        ExcessAccumulatorPostingService.PostingResult r = posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(r.posted()).isZero();
        assertThat(r.problems()).containsKey(ROTATION_EMP.toString());
        assertThat(r.problems().get(ROTATION_EMP.toString())).contains("Norm working hours");
    }

    @Test
    @DisplayName("one employee's problem does not stop the rest of the period posting")
    void oneFailureDoesNotHideTheRest() {
        UUID good = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(good, "OFFSHORE_HOURS", "220");
        // No profile at all — this one cannot be posted.
        UUID orphan = givenTimesheet(UNASSIGNED_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(orphan, "OFFSHORE_HOURS", "200");

        ExcessAccumulatorPostingService.PostingResult r = posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(r.posted()).isEqualTo(1);
        assertThat(r.rows()).singleElement()
                .satisfies(row -> assertThat(row.employeeId()).isEqualTo(ROTATION_EMP));
    }

    @Test
    @DisplayName("a posting failure never propagates out of the lock listener")
    void listenerSwallowsFailures() {
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("database on fire"));
        givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);

        // The period is already committed as locked. Throwing here would report a
        // failure for something that actually succeeded.
        posting.onPeriodLocked(new TimesheetPeriodLockedEvent(2026, 7, List.of(), "hr"));
    }

    @Test
    @DisplayName("re-posting a corrected month replaces it rather than adding a second")
    void repostReplaces() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "220");
        posting.post(2026, 7, "PERIOD_LOCK");

        totalRows.clear();
        givenTotal(ts, "OFFSHORE_HOURS", "200");
        ExcessAccumulatorPostingService.PostingResult r = posting.post(2026, 7, "REPOST");

        assertThat(monthRows).hasSize(1);
        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.actualHours()).isEqualByComparingTo("200");
            assertThat(row.runningBalance()).isEqualByComparingTo("16");
        });
        assertThat(monthRows.get(0).getSource()).isEqualTo("REPOST");
    }

    @Test
    @DisplayName("every posting run is audit logged")
    void postingIsAudited() {
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "220");
        posting.post(2026, 7, "PERIOD_LOCK");

        assertThat(audit.actions).containsExactly("ACCUMULATOR_POST");
    }

    @Test
    @DisplayName("an unconfigured category set refuses rather than totalling zero hours")
    void unconfiguredCategoriesRefuse() {
        CalculationProfile p = rotationProfile();
        p.setAccumulatorCategories(null);
        CalculationProfileRepository profiles = mock(CalculationProfileRepository.class);
        when(profiles.findByCode(anyString())).thenReturn(Optional.of(p));

        EmployeeCalculationProfileRepository assignments =
                mock(EmployeeCalculationProfileRepository.class);
        when(assignments.findByEmployeeIdOrderByEffectiveFromDesc(any()))
                .thenReturn(List.of(assignment(ROTATION_EMP, "OFFSHORE_ROTATION")));

        TimesheetRepository timesheets = mock(TimesheetRepository.class);
        UUID ts = givenTimesheet(ROTATION_EMP, 2026, 7, TimesheetStatus.LOCKED);
        givenTotal(ts, "OFFSHORE_HOURS", "220");
        when(timesheets.findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(anyInt(), anyInt()))
                .thenReturn(List.copyOf(timesheetRows));

        TimesheetMonthTotalRepository monthTotals = mock(TimesheetMonthTotalRepository.class);
        when(monthTotals.findByTimesheetIdOrderByCategoryCodeAsc(any()))
                .thenReturn(List.copyOf(totalRows));

        ExcessAccumulatorRepository accumulators = mock(ExcessAccumulatorRepository.class);
        ExcessAccumulatorMonthRepository months = mock(ExcessAccumulatorMonthRepository.class);
        BalancingPeriodDefRepository defs = mock(BalancingPeriodDefRepository.class);
        when(defs.findBySchemeCodeOrderByPeriodSeqAsc(anyString()))
                .thenReturn(List.of(def(2, 5, 8, 8)));

        ExcessAccumulatorPostingService svc = new ExcessAccumulatorPostingService(
                timesheets, monthTotals, normHours, assignments, profiles,
                new ExcessAccumulatorService(accumulators, months, defs), audit);

        ExcessAccumulatorPostingService.PostingResult r = svc.post(2026, 7, "PERIOD_LOCK");

        assertThat(r.posted()).isZero();
        assertThat(r.problems().get(ROTATION_EMP.toString())).contains("BLOCKERS Q6.1");
        verify(months, never()).save(any());
    }
}
