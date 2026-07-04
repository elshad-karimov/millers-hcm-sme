package az.millers.hcm.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins the avg + median helpers — the rest of the service walks the DB
 * and isn't worth a slice test here. Reflection-based since the helpers
 * are package-private statics; cleaner than promoting them just for the
 * test. Mirrors the M68 / M77 pattern of avoiding Mockito under Java 25.
 */
class RecruitmentAnalyticsServiceTest {

    private static Method m(String name) throws Exception {
        Method method = RecruitmentAnalyticsService.class.getDeclaredMethod(name, List.class);
        method.setAccessible(true);
        return method;
    }

    @Test
    void avgReturnsNullForEmpty() throws Exception {
        Method avg = m("avg");
        assertThat(avg.invoke(null, List.of())).isNull();
        assertThat(avg.invoke(null, (List<Long>) null)).isNull();
    }

    @Test
    void avgOfThreeIsArithmeticMean() throws Exception {
        Method avg = m("avg");
        BigDecimal result = (BigDecimal) avg.invoke(null, List.of(10L, 20L, 30L));
        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    void medianOdd() throws Exception {
        Method median = m("median");
        BigDecimal r = (BigDecimal) median.invoke(null, List.of(5L, 1L, 3L));
        assertThat(r).isEqualByComparingTo("3");
    }

    @Test
    void medianEvenAveragesMiddleTwo() throws Exception {
        Method median = m("median");
        BigDecimal r = (BigDecimal) median.invoke(null, List.of(1L, 2L, 3L, 4L));
        assertThat(r).isEqualByComparingTo("2.50");
    }

    @Test
    void medianSingleValue() throws Exception {
        Method median = m("median");
        BigDecimal r = (BigDecimal) median.invoke(null, List.of(42L));
        assertThat(r).isEqualByComparingTo("42");
    }

    @Test
    void medianEmptyIsNull() throws Exception {
        Method median = m("median");
        assertThat(median.invoke(null, List.of())).isNull();
    }
}
