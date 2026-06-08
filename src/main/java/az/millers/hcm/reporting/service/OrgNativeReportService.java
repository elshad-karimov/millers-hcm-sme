package az.millers.hcm.reporting.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.VersionStatus;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.organization.repo.LocationRepository;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.organization.repo.StructureVersionRepository;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.HeadcountReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.HeadcountRow;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.HrbpCoverageReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.HrbpCoverageRow;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.OrgDistributionReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.OrgFlatReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.OrgUnitFlatRow;

/**
 * M145 — org-native report computations (§35).
 *
 * <p>All reports are computed in-memory on top of the active structure
 * version's org-unit list plus the employee table (≤ 10⁵ rows per PRD).
 * If no active version exists the reports return empty results.
 */
@Service
public class OrgNativeReportService {

    private final StructureVersionRepository versions;
    private final OrgUnitRepository orgUnits;
    private final EmployeeRepository employees;
    private final LegalEntityRepository legalEntities;
    private final LocationRepository locations;

    public OrgNativeReportService(StructureVersionRepository versions,
                                   OrgUnitRepository orgUnits,
                                   EmployeeRepository employees,
                                   LegalEntityRepository legalEntities,
                                   LocationRepository locations) {
        this.versions = versions;
        this.orgUnits = orgUnits;
        this.employees = employees;
        this.legalEntities = legalEntities;
        this.locations = locations;
    }

    // ── Headcount vs budget ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HeadcountReport headcount() {
        List<OrgUnit> units = activeUnits();
        Map<UUID, Long> countByUnit = headcountMap();

        List<HeadcountRow> rows = new ArrayList<>(units.size());
        int totalBudget = 0;
        int totalActual = 0;
        for (OrgUnit u : units) {
            int actual = (int) (long) countByUnit.getOrDefault(u.getId(), 0L);
            Integer budget = u.getHeadcountBudget();
            Integer variance = budget != null ? budget - actual : null;
            if (budget != null) totalBudget += budget;
            totalActual += actual;
            rows.add(new HeadcountRow(
                    u.getId(), u.getCode(), u.getName(), u.getUnitType(),
                    u.getLifecycleState(), budget, actual, variance));
        }
        rows.sort(Comparator.comparing(HeadcountRow::name));
        Integer totalVariance = totalBudget > 0 ? totalBudget - totalActual : null;
        return new HeadcountReport(totalBudget, totalActual, totalVariance, rows);
    }

    // ── HRBP coverage ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HrbpCoverageReport hrbpCoverage() {
        List<OrgUnit> units = activeUnits();

        // Collect unique HRBP ids and resolve their names in one go.
        Map<UUID, String> hrbpNames = new HashMap<>();
        units.stream()
                .map(OrgUnit::getHrbpId)
                .filter(id -> id != null && !hrbpNames.containsKey(id))
                .forEach(id -> employees.findById(id).ifPresent(e ->
                        hrbpNames.put(id, e.getFirstName() + " " + e.getLastName()
                                + " (" + e.getEmployeeNo() + ")")));

        List<HrbpCoverageRow> rows = new ArrayList<>(units.size());
        for (OrgUnit u : units) {
            UUID hrbpId = u.getHrbpId();
            rows.add(new HrbpCoverageRow(
                    u.getId(), u.getCode(), u.getName(), u.getUnitType(),
                    hrbpId, hrbpId != null ? hrbpNames.get(hrbpId) : null,
                    hrbpId != null));
        }
        rows.sort(Comparator.comparing(HrbpCoverageRow::name));

        int withHrbp = (int) rows.stream().filter(HrbpCoverageRow::hasHrbp).count();
        int total = rows.size();
        double pct = total > 0 ? (withHrbp * 100.0 / total) : 0.0;
        return new HrbpCoverageReport(total, withHrbp, total - withHrbp, pct, rows);
    }

    // ── Distributions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrgDistributionReport distribution() {
        List<OrgUnit> units = activeUnits();

        Map<String, Long> byState = units.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getLifecycleState() != null
                                ? u.getLifecycleState().name() : "ACTIVE",
                        Collectors.counting()));

        Map<String, Long> byType = units.stream()
                .collect(Collectors.groupingBy(OrgUnit::getUnitType, Collectors.counting()));

        return new OrgDistributionReport(byState, byType);
    }

    // ── Flat export ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrgFlatReport flatExport() {
        List<OrgUnit> units = activeUnits();
        Map<UUID, OrgUnit> byId = units.stream()
                .collect(Collectors.toMap(OrgUnit::getId, u -> u));
        Map<UUID, Long> countByUnit = headcountMap();

        // Resolve HRBP employee nos in bulk.
        Map<UUID, String> hrbpNos = new HashMap<>();
        units.stream().map(OrgUnit::getHrbpId)
                .filter(id -> id != null && !hrbpNos.containsKey(id))
                .forEach(id -> employees.findById(id)
                        .ifPresent(e -> hrbpNos.put(id, e.getEmployeeNo())));

        // M222 — resolve legal entity + location codes in bulk.
        Map<UUID, String> legalEntityCodes = new HashMap<>();
        units.stream().map(OrgUnit::getLegalEntityId)
                .filter(id -> id != null && !legalEntityCodes.containsKey(id))
                .forEach(id -> legalEntities.findById(id)
                        .ifPresent(le -> legalEntityCodes.put(id, le.getCode())));

        Map<UUID, String> locationCodes = new HashMap<>();
        units.stream().map(OrgUnit::getLocationId)
                .filter(id -> id != null && !locationCodes.containsKey(id))
                .forEach(id -> locations.findById(id)
                        .ifPresent(loc -> locationCodes.put(id, loc.getCode())));

        List<OrgUnitFlatRow> rows = new ArrayList<>(units.size());
        for (OrgUnit u : units) {
            OrgUnit parent = u.getParentId() != null ? byId.get(u.getParentId()) : null;
            int actual = (int) (long) countByUnit.getOrDefault(u.getId(), 0L);
            rows.add(new OrgUnitFlatRow(
                    u.getId(), u.getCode(), u.getName(), u.getUnitType(),
                    parent != null ? parent.getCode() : null,
                    u.getLifecycleState() != null ? u.getLifecycleState().name() : "ACTIVE",
                    u.getLegalEntityId() != null ? legalEntityCodes.get(u.getLegalEntityId()) : null,
                    u.getLocationId() != null ? locationCodes.get(u.getLocationId()) : null,
                    u.getHrbpId() != null ? hrbpNos.get(u.getHrbpId()) : null,
                    u.getCostCentreCode(), u.getContactEmail(),
                    u.getHeadcountBudget(), actual,
                    u.getClosureAnnouncedDate(), u.getClosedDate()));
        }
        rows.sort(Comparator.comparing(OrgUnitFlatRow::code));
        return new OrgFlatReport(rows);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<OrgUnit> activeUnits() {
        return versions.findFirstByStatus(VersionStatus.ACTIVE)
                .map(v -> orgUnits.findByVersionIdOrderBySortOrderAscNameAsc(v.getId()))
                .orElse(List.of());
    }

    private Map<UUID, Long> headcountMap() {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : employees.countByOrgUnitIdAndEmploymentStatus(EmploymentStatus.ACTIVE)) {
            UUID unitId = (UUID) row[0];
            Long count = (Long) row[1];
            map.put(unitId, count);
        }
        return map;
    }
}
