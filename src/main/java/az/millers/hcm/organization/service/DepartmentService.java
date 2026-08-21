package az.millers.hcm.organization.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.StructureVersion;
import az.millers.hcm.organization.domain.VersionStatus;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.organization.repo.StructureVersionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Departments as a plain editable list.
 *
 * The organization module models structure as versions that go draft → submit →
 * approve → activate, and unit edits are DRAFT-only. That is the right shape for
 * an enterprise reorganisation signed off by several people. It is the wrong
 * shape for "we hired someone into a new department" — and the evidence is in
 * the data: three structure versions were started, none activated, and not one
 * unit created. Meanwhile the employee screen needs a department list to pick
 * from, so the field stayed free text and the source spreadsheet accumulated
 * thirteen spellings of the same handful of departments.
 *
 * This service maintains departments directly on the ACTIVE version, and audits
 * every change. It is a deliberate simplification for this edition, not a bug:
 * the versioned path is untouched and still available under Org Structure for
 * anyone who wants a reviewed reorganisation.
 *
 * The one thing it must never do is create an empty draft and activate it —
 * {@link OrgStructureService#activate} archives the current ACTIVE version, so
 * that would silently delete every department. A version is only ever created
 * here when there is no ACTIVE one to lose.
 */
@Service
public class DepartmentService {

    /** Unit type used for departments; seeded by the org-unit-type master. */
    static final String DEPARTMENT = "DEPARTMENT";
    /** Root the departments hang from — DEPARTMENT itself is not root-level. */
    static final String COMPANY = "COMPANY";
    private static final String ROOT_CODE = "ORG";

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "Department";

    private final StructureVersionRepository versions;
    private final OrgUnitRepository units;
    private final OrgUnitTypeConfigService typeConfigs;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public DepartmentService(StructureVersionRepository versions,
                             OrgUnitRepository units,
                             OrgUnitTypeConfigService typeConfigs,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.versions = versions;
        this.units = units;
        this.typeConfigs = typeConfigs;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** Every department on the active structure, or empty when none exists yet. */
    @Transactional(readOnly = true)
    public List<OrgUnit> list() {
        return activeVersion()
                .map(v -> units.findByVersionIdOrderBySortOrderAscNameAsc(v.getId()).stream()
                        .filter(u -> DEPARTMENT.equals(u.getUnitType()))
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public OrgUnit create(String code, String name) {
        String cleanCode = require(code, "code");
        String cleanName = require(name, "name");

        StructureVersion version = activeVersionOrCreate();
        if (units.existsByVersionIdAndCode(version.getId(), cleanCode)) {
            throw new BadRequestException("A department with code " + cleanCode + " already exists");
        }
        typeConfigs.validate(DEPARTMENT);

        OrgUnit unit = new OrgUnit();
        unit.setVersionId(version.getId());
        unit.setCode(cleanCode);
        unit.setName(cleanName);
        unit.setUnitType(DEPARTMENT);
        unit.setParentId(rootUnit(version).getId());
        OrgUnit saved = units.save(unit);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null,
                java.util.Map.of("code", cleanCode, "name", cleanName));
        return saved;
    }

    @Transactional
    public OrgUnit rename(UUID id, String name) {
        OrgUnit unit = find(id);
        String cleanName = require(name, "name");
        String before = unit.getName();
        unit.setName(cleanName);
        OrgUnit saved = units.save(unit);
        audit.record(MODULE, ENTITY, id.toString(), "RENAME",
                java.util.Map.of("name", before), java.util.Map.of("name", cleanName));
        return saved;
    }

    /**
     * Removes a department. Refused while anything hangs beneath it — an
     * orphaned sub-unit would vanish from the tree without saying so.
     *
     * Employees already recorded against it keep their stored department name:
     * the employee row holds the name as well as the id precisely so history
     * does not rewrite itself when the list changes.
     */
    @Transactional
    public void delete(UUID id) {
        OrgUnit unit = find(id);
        if (units.existsByParentId(id)) {
            throw new BadRequestException(
                    "This department has units beneath it. Remove those first.");
        }
        audit.record(MODULE, ENTITY, id.toString(), "DELETE",
                java.util.Map.of("code", unit.getCode(), "name", unit.getName()), null);
        units.delete(unit);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Optional<StructureVersion> activeVersion() {
        return versions.findFirstByStatus(VersionStatus.ACTIVE);
    }

    /**
     * The active structure, created on first use.
     *
     * Only ever creates when NOTHING is active, so this can never archive a
     * live structure — see the class comment.
     */
    private StructureVersion activeVersionOrCreate() {
        return activeVersion().orElseGet(() -> {
            StructureVersion v = new StructureVersion();
            v.setVersionNumber((int) versions.nextVersionNumber());
            v.setEffectiveDate(LocalDate.now());
            v.setStatus(VersionStatus.ACTIVE);
            v.setChangeReason("Created automatically for the department list");
            v.setCreatedBy(currentRequest.username());
            v.setActivatedAt(OffsetDateTime.now());
            StructureVersion saved = versions.save(v);
            audit.record(MODULE, "StructureVersion", saved.getId().toString(),
                    "CREATE_ACTIVE", null,
                    java.util.Map.of("reason", "first department added"));
            return saved;
        });
    }

    /** The company node departments sit under, created with the structure. */
    private OrgUnit rootUnit(StructureVersion version) {
        return units.findByVersionIdOrderBySortOrderAscNameAsc(version.getId()).stream()
                .filter(u -> u.getParentId() == null)
                .findFirst()
                .orElseGet(() -> {
                    OrgUnit root = new OrgUnit();
                    root.setVersionId(version.getId());
                    root.setCode(ROOT_CODE);
                    root.setName("Organization");
                    root.setUnitType(COMPANY);
                    return units.save(root);
                });
    }

    private OrgUnit find(UUID id) {
        OrgUnit unit = units.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        if (!DEPARTMENT.equals(unit.getUnitType())) {
            throw new BadRequestException("That org unit is not a department");
        }
        return unit;
    }

    private static String require(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(field + " is required");
        }
        return trimmed;
    }
}
