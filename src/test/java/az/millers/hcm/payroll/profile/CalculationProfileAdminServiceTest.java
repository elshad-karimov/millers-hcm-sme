package az.millers.hcm.payroll.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.AssignProfile;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.SettleExcess;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpdateProfileSettings;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpsertMewaRule;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpsertNormHours;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHoursRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Configuring how people are paid.
 *
 * <p>Two themes run through these tests. First, **answering an open question is
 * a configuration change**: setting the rotation multiplier is what turns a
 * refusing engine into a paying one, so it has to be deliberate, reasoned and
 * audited. Second, **overlapping effective dates are the quiet killer** — two
 * assignments covering one month would make someone's pay depend on row order,
 * so they are closed or rejected rather than allowed.
 */
class CalculationProfileAdminServiceTest {

    private static final UUID EMPLOYEE = UUID.randomUUID();

    private final List<EmployeeCalculationProfile> assignmentRows = new ArrayList<>();
    private final List<EmployeeMewaRule> mewaRows = new ArrayList<>();

    private CalculationProfileRepository profiles;
    private EmployeeCalculationProfileRepository assignments;
    private EmployeeMewaRuleRepository mewaRules;
    private PeriodNormHoursRepository normHours;
    private EmployeeCompensationRepository compensations;
    private ExcessAccumulatorRepository accumulators;
    private RecordingAudit audit;
    private CalculationProfileAdminService admin;

    /** This JDK cannot mock concrete classes; capture by subclassing instead. */
    static final class RecordingAudit extends AuditService {
        record Entry(String action, Object oldValue, Object newValue) {
        }

        final List<Entry> entries = new ArrayList<>();

        RecordingAudit() {
            super(null, null, null);
        }

        @Override
        public void record(String module, String entityName, String entityId,
                           String action, Object oldValue, Object newValue) {
            entries.add(new Entry(action, oldValue, newValue));
        }

        List<String> actions() {
            return entries.stream().map(Entry::action).toList();
        }
    }

    @BeforeEach
    void setUp() {
        assignmentRows.clear();
        mewaRows.clear();

        profiles = mock(CalculationProfileRepository.class);
        assignments = mock(EmployeeCalculationProfileRepository.class);
        mewaRules = mock(EmployeeMewaRuleRepository.class);
        normHours = mock(PeriodNormHoursRepository.class);
        compensations = mock(EmployeeCompensationRepository.class);
        accumulators = mock(ExcessAccumulatorRepository.class);
        EmployeeRepository employees = mock(EmployeeRepository.class);
        ExcessAccumulatorMonthRepository months = mock(ExcessAccumulatorMonthRepository.class);
        BalancingPeriodDefRepository defs = mock(BalancingPeriodDefRepository.class);
        audit = new RecordingAudit();

        when(employees.existsById(any())).thenReturn(true);

        when(assignments.findByEmployeeIdOrderByEffectiveFromDesc(any()))
                .thenAnswer(inv -> List.copyOf(assignmentRows));
        when(assignments.save(any())).thenAnswer(inv -> {
            EmployeeCalculationProfile a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
                assignmentRows.add(a);
            }
            return a;
        });
        when(mewaRules.findByEmployeeIdOrderByEffectiveFromDesc(any()))
                .thenAnswer(inv -> List.copyOf(mewaRows));
        when(mewaRules.save(any())).thenAnswer(inv -> {
            EmployeeMewaRule r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
                mewaRows.add(r);
            }
            return r;
        });
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(normHours.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accumulators.save(any())).thenAnswer(inv -> {
            ExcessAccumulator a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        ExcessAccumulatorService accumulatorService =
                new ExcessAccumulatorService(accumulators, months, defs);

        admin = new CalculationProfileAdminService(profiles, assignments, mewaRules, normHours,
                employees, compensations, accumulatorService, audit, new CurrentRequest());
    }

    // ---- fixtures ----------------------------------------------------------

    private static CalculationProfile rotation() {
        CalculationProfile p = new CalculationProfile();
        p.setCode("OFFSHORE_ROTATION");
        p.setName("rotation");
        p.setOffshoreSalaryMode(CalculationProfile.OFFSHORE_MONTHLY_BASE);
        p.setOffshoreMultiplier(new BigDecimal("1.75"));
        p.setExcessMethod(CalculationProfile.EXCESS_BALANCING_PERIOD);
        p.setBalancingSchemeCode(BalancingScheme.OFFSHORE_4_MONTH);
        p.setAccumulatorCategories("OFFSHORE_HOURS,ONSHORE_HOURS");
        return p;                                  // excessMultiplier deliberately null
    }

    private static CalculationProfile onshoreFixed() {
        CalculationProfile p = new CalculationProfile();
        p.setCode("ONSHORE_FIXED");
        p.setName("onshore");
        p.setOffshoreSalaryMode(CalculationProfile.OFFSHORE_NONE);
        p.setExcessMethod(CalculationProfile.EXCESS_NONE);
        return p;
    }

    private static UpdateProfileSettings settings(String excessMult, Boolean night,
                                                  String categories, String reason) {
        return new UpdateProfileSettings(
                excessMult == null ? null : new BigDecimal(excessMult),
                night, categories, null, null, reason);
    }

    // ---- answering the open questions ---------------------------------------

    @Test
    @DisplayName("answering Q2 sets the rotation multiplier and audits the change")
    void answerQ2() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));

        CalculationProfile saved = admin.updateSettings("OFFSHORE_ROTATION",
                settings("3.5", null, null, "Emil confirmed 2 x 1.75 on the April payroll"));

        assertThat(saved.getExcessMultiplier()).isEqualByComparingTo("3.5");
        assertThat(audit.actions()).containsExactly("PROFILE_SETTINGS_UPDATE");

        // The old value has to be in the trail, or the change is not reviewable.
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) audit.entries.get(0).oldValue();
        assertThat(before.get("excessMultiplier")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) audit.entries.get(0).newValue();
        assertThat(after.get("excessMultiplier")).isEqualTo("3.5");
        assertThat(after.get("reason")).isEqualTo("Emil confirmed 2 x 1.75 on the April payroll");
    }

    @Test
    @DisplayName("answering Q2 does not accidentally assert an answer to Q1")
    void nullMeansLeaveAlone() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));

        CalculationProfile saved = admin.updateSettings("OFFSHORE_ROTATION",
                settings("3.5", null, null, "answering Q2 only"));

        assertThat(saved.getNightHoursSeparateFromBase()).isNull();
        assertThat(saved.nightTreatmentUnconfirmed()).isTrue();
    }

    @Test
    @DisplayName("a setting can be returned to unresolved, which makes the engine refuse again")
    void clearSetting() {
        CalculationProfile p = rotation();
        p.setExcessMultiplier(new BigDecimal("3.5"));
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(p));

        CalculationProfile saved = admin.clearSetting("OFFSHORE_ROTATION", "excessMultiplier",
                "Emil withdrew the confirmation pending the August run");

        assertThat(saved.getExcessMultiplier()).isNull();
        assertThat(audit.actions()).containsExactly("PROFILE_SETTING_CLEARED");
    }

    @Test
    @DisplayName("clearing without a reason is refused — it stops payments silently")
    void clearNeedsReason() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));
        assertThatThrownBy(() -> admin.clearSetting("OFFSHORE_ROTATION", "excessMultiplier", "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    @DisplayName("an unknown setting name is refused rather than silently doing nothing")
    void clearUnknownSetting() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));
        assertThatThrownBy(() -> admin.clearSetting("OFFSHORE_ROTATION", "offshoreMultiplier", "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a clearable setting");
    }

    @Test
    @DisplayName("a multiplier on a profile that never settles excess is refused")
    void multiplierOnNonSettlingProfile() {
        when(profiles.findByCode("ONSHORE_FIXED")).thenReturn(Optional.of(onshoreFixed()));
        assertThatThrownBy(() -> admin.updateSettings("ONSHORE_FIXED",
                settings("3.5", null, null, "typo")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not settle excess");
    }

    @Test
    @DisplayName("an empty category list is refused — it would total zero hours every month")
    void emptyCategoriesRefused() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));
        assertThatThrownBy(() -> admin.updateSettings("OFFSHORE_ROTATION",
                settings(null, null, " , , ", "oops")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty category list");
    }

    @Test
    @DisplayName("category lists are tidied and de-duplicated")
    void categoriesNormalised() {
        when(profiles.findByCode("OFFSHORE_ROTATION")).thenReturn(Optional.of(rotation()));
        CalculationProfile saved = admin.updateSettings("OFFSHORE_ROTATION",
                settings(null, null, " OFFSHORE_HOURS , ONSHORE_HOURS ,OFFSHORE_HOURS", "tidy"));
        assertThat(saved.getAccumulatorCategories()).isEqualTo("OFFSHORE_HOURS,ONSHORE_HOURS");
    }

    @Test
    @DisplayName("an unknown profile code is a 404, not a silent create")
    void unknownProfile() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> admin.updateSettings("NOPE", settings("3.5", null, null, "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- assignment and effective dating -------------------------------------

    @Test
    @DisplayName("assigning a new profile closes the previous open one the day before")
    void assignClosesPrevious() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.of(rotation()));

        admin.assign(new AssignProfile(EMPLOYEE, "ONSHORE_FIXED",
                LocalDate.of(2026, 1, 1), null, "initial"));
        admin.assign(new AssignProfile(EMPLOYEE, "OFFSHORE_ROTATION",
                LocalDate.of(2026, 7, 1), null, "moved to rotation"));

        assertThat(assignmentRows).hasSize(2);
        EmployeeCalculationProfile first = assignmentRows.get(0);
        assertThat(first.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 6, 30));
        // No month is covered by two profiles.
        assertThat(first.coversPeriodStart(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(assignmentRows.get(1).coversPeriodStart(LocalDate.of(2026, 7, 1))).isTrue();
    }

    @Test
    @DisplayName("an overlapping backdated assignment is refused, not silently ranked")
    void overlapRefused() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.of(rotation()));
        admin.assign(new AssignProfile(EMPLOYEE, "ONSHORE_FIXED",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "closed range"));

        assertThatThrownBy(() -> admin.assign(new AssignProfile(EMPLOYEE, "OFFSHORE_ROTATION",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), "overlaps")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("row order");
    }

    @Test
    @DisplayName("an end date before the start date is refused")
    void backwardsRange() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.of(rotation()));
        assertThatThrownBy(() -> admin.assign(new AssignProfile(EMPLOYEE, "OFFSHORE_ROTATION",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1), "backwards")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("assigning a profile that does not exist is refused")
    void assignUnknownProfile() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> admin.assign(new AssignProfile(EMPLOYEE, "MADE_UP",
                LocalDate.of(2026, 7, 1), null, "typo")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("every assignment is audit logged with its reason")
    void assignIsAudited() {
        when(profiles.findByCode(anyString())).thenReturn(Optional.of(rotation()));
        admin.assign(new AssignProfile(EMPLOYEE, "OFFSHORE_ROTATION",
                LocalDate.of(2026, 7, 1), null, "new rotation contract"));
        assertThat(audit.actions()).containsExactly("PROFILE_ASSIGNED");
    }

    // ---- MEWA -----------------------------------------------------------------

    @Test
    @DisplayName("a new MEWA rate closes the previous one rather than stacking")
    void mewaClosesPrevious() {
        admin.upsertMewa(new UpsertMewaRule(EMPLOYEE, new BigDecimal("0.30"),
                LocalDate.of(2026, 1, 1), null, "initial 30%"));
        admin.upsertMewa(new UpsertMewaRule(EMPLOYEE, new BigDecimal("0.60"),
                LocalDate.of(2026, 7, 1), null, "raised to 60%"));

        assertThat(mewaRows).hasSize(2);
        assertThat(mewaRows.get(0).getEffectiveTo()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(mewaRows.get(0).coversPeriodStart(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(mewaRows.get(1).coversPeriodStart(LocalDate.of(2026, 7, 1))).isTrue();
        assertThat(audit.actions()).containsExactly("MEWA_SET", "MEWA_SET");
    }

    // ---- norm hours -------------------------------------------------------------

    @Test
    @DisplayName("setting norm hours for a new period is audited as SET")
    void normHoursSet() {
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        admin.upsertNormHours(new UpsertNormHours(2026, 8, new BigDecimal("168")));
        assertThat(audit.actions()).containsExactly("NORM_HOURS_SET");
    }

    @Test
    @DisplayName("changing an existing norm is audited as CHANGED, with the old value")
    void normHoursChanged() {
        PeriodNormHours existing = new PeriodNormHours();
        existing.setPeriodYear(2026);
        existing.setPeriodMonth(7);
        existing.setNormHours(new BigDecimal("184"));
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.of(existing));

        admin.upsertNormHours(new UpsertNormHours(2026, 7, new BigDecimal("176")));

        // Changing the norm re-rates every hour in that month, so the previous
        // value has to survive in the trail.
        assertThat(audit.actions()).containsExactly("NORM_HOURS_CHANGED");
        assertThat(audit.entries.get(0).oldValue().toString()).contains("184");
    }

    // ---- settlement --------------------------------------------------------------

    private void givenRotationEmployee(CalculationProfile profile) {
        EmployeeCalculationProfile a = new EmployeeCalculationProfile();
        a.setEmployeeId(EMPLOYEE);
        a.setProfileCode(profile.getCode());
        a.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        assignmentRows.add(a);
        when(profiles.findByCode(profile.getCode())).thenReturn(Optional.of(profile));
    }

    private void givenPayContext() {
        PeriodNormHours n = new PeriodNormHours();
        n.setNormHours(new BigDecimal("184"));
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.of(n));

        EmployeeCompensation c = new EmployeeCompensation();
        c.setMonthlyBaseSalary(new BigDecimal("3500"));
        when(compensations.findActiveOn(any(), any())).thenReturn(Optional.of(c));
    }

    private void givenOpenAccumulator(String balance) {
        ExcessAccumulator acc = new ExcessAccumulator();
        acc.setId(UUID.randomUUID());
        acc.setEmployeeId(EMPLOYEE);
        acc.setPeriodYear(2026);
        acc.setPeriodSeq(2);
        acc.setStatus(ExcessAccumulator.OPEN);
        acc.setBalanceHours(new BigDecimal(balance));
        when(accumulators.findByEmployeeIdAndPeriodYearAndPeriodSeq(any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(acc));
    }

    private static SettleExcess settleRequest() {
        return new SettleExcess(EMPLOYEE, 2026, 2, 2026, 8, "August settlement");
    }

    @Test
    @DisplayName("BLOCKERS Q2 — settling is refused while the multiplier is unresolved")
    void settleRefusesWithoutMultiplier() {
        givenRotationEmployee(rotation());
        givenPayContext();
        givenOpenAccumulator("60");

        assertThatThrownBy(() -> admin.settle(settleRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BLOCKERS Q2");
    }

    @Test
    @DisplayName("once answered, settlement prices the hours at the employee's own rate")
    void settlePricesAtLookedUpRate() {
        CalculationProfile p = rotation();
        p.setExcessMultiplier(new BigDecimal("3.5"));
        givenRotationEmployee(p);
        givenPayContext();
        givenOpenAccumulator("60");

        ExcessAccumulator settled = admin.settle(settleRequest());

        // 60 x (3500 / 184) x 3.5 — the rate is derived, never passed in.
        assertThat(settled.getSettledAmount()).isEqualByComparingTo("3994.57");
        assertThat(settled.getSettledExcessHours()).isEqualByComparingTo("60");
        assertThat(settled.getSettledInPeriodMonth()).isEqualTo(8);
        assertThat(settled.isSettled()).isTrue();
    }

    @Test
    @DisplayName("settling an employee with no profile is refused")
    void settleWithoutProfile() {
        givenPayContext();
        assertThatThrownBy(() -> admin.settle(settleRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no calculation profile");
    }

    @Test
    @DisplayName("settling a non-balancing profile is refused")
    void settleNonBalancingProfile() {
        givenRotationEmployee(onshoreFixed());
        givenPayContext();
        assertThatThrownBy(() -> admin.settle(settleRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not use balancing-period settlement");
    }

    @Test
    @DisplayName("settling without norm hours for the paying month is refused")
    void settleWithoutNorm() {
        CalculationProfile p = rotation();
        p.setExcessMultiplier(new BigDecimal("3.5"));
        givenRotationEmployee(p);
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> admin.settle(settleRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Norm working hours");
    }

    @Test
    @DisplayName("settling without a compensation record is refused")
    void settleWithoutCompensation() {
        CalculationProfile p = rotation();
        p.setExcessMultiplier(new BigDecimal("3.5"));
        givenRotationEmployee(p);
        PeriodNormHours n = new PeriodNormHours();
        n.setNormHours(new BigDecimal("184"));
        when(normHours.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Optional.of(n));
        when(compensations.findActiveOn(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> admin.settle(settleRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no effective compensation record");
    }
}
