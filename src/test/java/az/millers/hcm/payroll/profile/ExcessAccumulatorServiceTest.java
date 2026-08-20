package az.millers.hcm.payroll.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.BadRequestException;

/**
 * The balancing-period ledger, pinned to the company's own worked examples.
 *
 * <p>Rotation staff do not have their overtime decided monthly: hours
 * accumulate against the norm across Jan–Apr, May–Aug and Sep–Dec, and only the
 * closing difference is payable. Both fixtures below include a month that falls
 * <em>below</em> norm, which is the case a naive monthly-overtime implementation
 * gets wrong — it would pay the surplus months and ignore the shortfall.
 *
 * <p>In-memory repositories rather than a database: the arithmetic and the
 * refusals are the risk here, not the persistence mapping.
 */
class ExcessAccumulatorServiceTest {

    private static JsonNode fixtures;

    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final String SCHEME = BalancingScheme.OFFSHORE_4_MONTH;

    private final List<ExcessAccumulator> accumulatorRows = new ArrayList<>();
    private final List<ExcessAccumulatorMonth> monthRows = new ArrayList<>();

    private ExcessAccumulatorService service;

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = ExcessAccumulatorServiceTest.class
                .getResourceAsStream("/fixtures/july-2026-worked-examples.json")) {
            fixtures = new ObjectMapper().readTree(in);
        }
    }

    @BeforeEach
    void setUp() {
        accumulatorRows.clear();
        monthRows.clear();

        ExcessAccumulatorRepository accumulators = mock(ExcessAccumulatorRepository.class);
        ExcessAccumulatorMonthRepository months = mock(ExcessAccumulatorMonthRepository.class);
        BalancingPeriodDefRepository periodDefs = mock(BalancingPeriodDefRepository.class);

        when(periodDefs.findBySchemeCodeOrderByPeriodSeqAsc(anyString()))
                .thenReturn(seededPeriods());

        when(accumulators.save(any())).thenAnswer(inv -> {
            ExcessAccumulator a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
                accumulatorRows.add(a);
            }
            return a;
        });
        when(accumulators.findByEmployeeIdAndPeriodYearAndPeriodSeq(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> accumulatorRows.stream()
                        .filter(a -> a.getEmployeeId().equals(inv.getArgument(0))
                                && a.getPeriodYear() == (int) inv.getArgument(1)
                                && a.getPeriodSeq() == (int) inv.getArgument(2))
                        .findFirst());
        when(accumulators.findByEmployeeIdOrderByPeriodYearDescPeriodSeqDesc(any()))
                .thenAnswer(inv -> accumulatorRows.stream()
                        .filter(a -> a.getEmployeeId().equals(inv.getArgument(0)))
                        .toList());

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

        service = new ExcessAccumulatorService(accumulators, months, periodDefs);
    }

    /** The windows seeded by V327: Jan–Apr, May–Aug, Sep–Dec. */
    private static List<BalancingPeriodDef> seededPeriods() {
        return List.of(period(1, 1, 4, 4), period(2, 5, 8, 8), period(3, 9, 12, 12));
    }

    private static BalancingPeriodDef period(int seq, int start, int end, int settlement) {
        BalancingPeriodDef d = new BalancingPeriodDef();
        d.setSchemeCode(SCHEME);
        d.setPeriodSeq(seq);
        d.setStartMonth(start);
        d.setEndMonth(end);
        d.setSettlementMonth(settlement);
        return d;
    }

    /** Replay one fixture's months into the ledger. */
    private ExcessAccumulator replay(String caseName) {
        JsonNode c = fixtureCase(caseName);
        ExcessAccumulator acc = null;
        for (JsonNode m : c.get("months")) {
            String[] ym = m.get("month").asText().split("-");
            acc = service.recordMonth(EMPLOYEE, SCHEME,
                    Integer.parseInt(ym[0]), Integer.parseInt(ym[1]),
                    m.get("actual").decimalValue(), m.get("norm").decimalValue(), "test");
        }
        return acc;
    }

    private static JsonNode fixtureCase(String caseName) {
        for (JsonNode n : fixtures.get("balancingAccumulator")) {
            if (n.get("case").asText().equals(caseName)) return n;
        }
        throw new AssertionError("No accumulator fixture '" + caseName + "'");
    }

    // ---- the two worked ledgers -------------------------------------------

    @Test
    @DisplayName("Jan–Apr 2026 settles 64 hours: +50 −30 +12 +32")
    void janAprLedger() {
        ExcessAccumulator acc = replay("jan-apr-2026");
        assertThat(acc.payableHours()).isEqualByComparingTo(
                fixtureCase("jan-apr-2026").get("expected").get("settledExcessHours").decimalValue());
    }

    @Test
    @DisplayName("May–Aug 2026 settles 60 hours, and the balance goes negative in June")
    void mayAugLedger() {
        ExcessAccumulator acc = replay("may-aug-2026");
        assertThat(acc.payableHours()).isEqualByComparingTo(
                fixtureCase("may-aug-2026").get("expected").get("settledExcessHours").decimalValue());

        // The shortfall genuinely offsets the surplus — that is summarised
        // accounting, and it is what a monthly overtime rule would get wrong.
        BigDecimal june = monthRows.stream()
                .filter(m -> m.getPeriodMonth() == 6)
                .findFirst().orElseThrow().getRunningBalance();
        assertThat(june).isEqualByComparingTo("-8");
    }

    @Test
    @DisplayName("every month's running balance matches the fixture, month by month")
    void runningBalancesMatchFixture() {
        replay("may-aug-2026");
        for (JsonNode m : fixtureCase("may-aug-2026").get("months")) {
            int month = Integer.parseInt(m.get("month").asText().split("-")[1]);
            ExcessAccumulatorMonth row = monthRows.stream()
                    .filter(r -> r.getPeriodMonth() == month)
                    .findFirst().orElseThrow();
            assertThat(row.getDeltaHours())
                    .as("delta for month %d", month)
                    .isEqualByComparingTo(m.get("delta").decimalValue());
            assertThat(row.getRunningBalance())
                    .as("running balance for month %d", month)
                    .isEqualByComparingTo(m.get("running").decimalValue());
        }
    }

    // ---- when the hours become payable --------------------------------------

    @Test
    @DisplayName("months 1–3 of a period release nothing")
    void nothingDueBeforeSettlementMonth() {
        replay("may-aug-2026");
        assertThat(service.dueThisMonth(EMPLOYEE, SCHEME, 2026, 5)).isEmpty();
        assertThat(service.dueThisMonth(EMPLOYEE, SCHEME, 2026, 6)).isEmpty();
        assertThat(service.dueThisMonth(EMPLOYEE, SCHEME, 2026, 7)).isEmpty();
    }

    @Test
    @DisplayName("August releases the accumulated 60 hours")
    void dueInSettlementMonth() {
        replay("may-aug-2026");
        assertThat(service.dueThisMonth(EMPLOYEE, SCHEME, 2026, 8))
                .hasValueSatisfying(h -> assertThat(h).isEqualByComparingTo("60"));
    }

    @Test
    @DisplayName("a period that never exceeds norm pays nothing and creates no debt")
    void belowNormPaysNothing() {
        service.recordMonth(EMPLOYEE, SCHEME, 2026, 1,
                new BigDecimal("120"), new BigDecimal("160"), "test");
        ExcessAccumulator acc = service.recordMonth(EMPLOYEE, SCHEME, 2026, 2,
                new BigDecimal("140"), new BigDecimal("160"), "test");

        assertThat(acc.getBalanceHours()).isEqualByComparingTo("-60");
        assertThat(acc.payableHours()).isEqualByComparingTo("0");
    }

    // ---- corrections and settlement -------------------------------------------

    @Test
    @DisplayName("re-recording a corrected month recomputes every later balance")
    void correctionRecomputesLaterMonths() {
        replay("may-aug-2026");

        // July is corrected from 220 down to 200 actual hours: 20 fewer.
        ExcessAccumulator acc = service.recordMonth(EMPLOYEE, SCHEME, 2026, 7,
                new BigDecimal("200"), new BigDecimal("184"), "test");

        assertThat(acc.getBalanceHours()).isEqualByComparingTo("40");
        assertThat(monthRows.stream().filter(m -> m.getPeriodMonth() == 8)
                .findFirst().orElseThrow().getRunningBalance()).isEqualByComparingTo("40");
        // Still one row for July, not two.
        assertThat(monthRows.stream().filter(m -> m.getPeriodMonth() == 7).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("BLOCKERS Q2 — settling without a multiplier is refused, not guessed")
    void settleRefusesWithoutMultiplier() {
        replay("may-aug-2026");
        assertThatThrownBy(() -> service.settle(EMPLOYEE, 2026, 2, null,
                new BigDecimal("19.0217391304"), "test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BLOCKERS Q2");
    }

    @Test
    @DisplayName("settling records the hours, multiplier and amount actually used")
    void settleRecordsHowItWasDecided() {
        replay("may-aug-2026");
        ExcessAccumulator acc = service.settle(EMPLOYEE, 2026, 2,
                new BigDecimal("3.5"), new BigDecimal("19.0217391304"), "test");

        assertThat(acc.isSettled()).isTrue();
        assertThat(acc.getSettledExcessHours()).isEqualByComparingTo("60");
        assertThat(acc.getSettledMultiplier()).isEqualByComparingTo("3.5");
        assertThat(acc.getSettledAmount()).isEqualByComparingTo("3994.57");
        assertThat(acc.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("a period is never settled twice")
    void settleIsIdempotentlyRefused() {
        replay("may-aug-2026");
        service.settle(EMPLOYEE, 2026, 2, new BigDecimal("3.5"),
                new BigDecimal("19.0217391304"), "test");

        assertThatThrownBy(() -> service.settle(EMPLOYEE, 2026, 2, new BigDecimal("3.5"),
                new BigDecimal("19.0217391304"), "test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already settled");
    }

    @Test
    @DisplayName("a correction to a settled period is refused — it belongs in the next one")
    void settledPeriodIsNotRewritten() {
        replay("may-aug-2026");
        service.settle(EMPLOYEE, 2026, 2, new BigDecimal("3.5"),
                new BigDecimal("19.0217391304"), "test");

        assertThatThrownBy(() -> service.recordMonth(EMPLOYEE, SCHEME, 2026, 7,
                new BigDecimal("300"), new BigDecimal("184"), "test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("next open period");
    }

    @Test
    @DisplayName("each period keeps its own balance — August does not leak into September")
    void periodsAreSeparate() {
        replay("may-aug-2026");
        ExcessAccumulator sept = service.recordMonth(EMPLOYEE, SCHEME, 2026, 9,
                new BigDecimal("190"), new BigDecimal("176"), "test");

        assertThat(sept.getPeriodSeq()).isEqualTo(3);
        assertThat(sept.getBalanceHours()).isEqualByComparingTo("14");
        assertThat(service.ledgersFor(EMPLOYEE)).hasSize(2);
    }

    @Test
    @DisplayName("the ledger reads back month by month, so a settlement is traceable")
    void ledgerIsReadable() {
        replay("jan-apr-2026");
        List<ExcessAccumulatorService.Ledger> ledgers = service.ledgersFor(EMPLOYEE);

        assertThat(ledgers).hasSize(1);
        assertThat(ledgers.get(0).months()).hasSize(4);
        assertThat(ledgers.get(0).months().get(3).getRunningBalance())
                .isEqualByComparingTo("64");
    }

    @Test
    @DisplayName("a month outside every window is refused rather than silently dropped")
    void unmappedMonthRefused() {
        BalancingPeriodDefRepository gappy = mock(BalancingPeriodDefRepository.class);
        when(gappy.findBySchemeCodeOrderByPeriodSeqAsc(anyString()))
                .thenReturn(List.of(period(1, 1, 4, 4)));
        ExcessAccumulatorService partial = new ExcessAccumulatorService(
                mock(ExcessAccumulatorRepository.class),
                mock(ExcessAccumulatorMonthRepository.class), gappy);

        assertThatThrownBy(() -> partial.recordMonth(EMPLOYEE, SCHEME, 2026, 9,
                new BigDecimal("190"), new BigDecimal("176"), "test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no period covering month");
    }
}
