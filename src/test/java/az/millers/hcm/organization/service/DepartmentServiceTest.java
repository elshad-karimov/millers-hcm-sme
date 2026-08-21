package az.millers.hcm.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.StructureVersion;
import az.millers.hcm.organization.domain.VersionStatus;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.organization.repo.StructureVersionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * The department list writes straight into the ACTIVE structure version, which
 * is a deliberate simplification of the module's draft → approve → activate
 * cycle. One rule makes that safe, and it is the rule this pins:
 *
 *   a structure version is created ONLY when nothing is active.
 *
 * OrgStructureService.activate() archives whatever is currently ACTIVE. So a
 * version created while one is already live would, on activation, take every
 * existing department out of the structure — silently, because nothing errors.
 */
class DepartmentServiceTest {

    private StructureVersionRepository versions;
    private OrgUnitRepository units;
    private OrgUnitTypeConfigService typeConfigs;
    private DepartmentService service;

    @BeforeEach
    void setUp() {
        versions = mock(StructureVersionRepository.class);
        units = mock(OrgUnitRepository.class);
        typeConfigs = mock(OrgUnitTypeConfigService.class);
        AuditService audit = mock(AuditService.class);
        CurrentRequest currentRequest = mock(CurrentRequest.class);

        lenient().when(currentRequest.username()).thenReturn("tester");
        lenient().when(units.save(any(OrgUnit.class))).thenAnswer(i -> {
            OrgUnit u = i.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        lenient().when(versions.save(any(StructureVersion.class))).thenAnswer(i -> {
            StructureVersion v = i.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        service = new DepartmentService(versions, units, typeConfigs, audit, currentRequest);
    }

    @Test
    @DisplayName("with a structure already active, no new version is created")
    void reusesTheActiveVersion() {
        StructureVersion active = activeVersion();
        when(versions.findFirstByStatus(VersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(units.findByVersionIdOrderBySortOrderAscNameAsc(active.getId()))
                .thenReturn(List.of(root(active)));

        service.create("ENG", "Engineering");

        // The load-bearing assertion: creating a version here would archive the
        // live structure the moment it was activated, taking every department
        // with it.
        verify(versions, never()).save(any(StructureVersion.class));
    }

    @Test
    @DisplayName("with nothing active, the first department creates the structure")
    void createsTheStructureOnFirstUse() {
        when(versions.findFirstByStatus(VersionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(versions.nextVersionNumber()).thenReturn(1L);
        when(units.findByVersionIdOrderBySortOrderAscNameAsc(any())).thenReturn(List.of());

        service.create("ENG", "Engineering");

        verify(versions).save(any(StructureVersion.class));
        // Root company node plus the department itself.
        verify(units, org.mockito.Mockito.times(2)).save(any(OrgUnit.class));
    }

    @Test
    @DisplayName("a duplicate code is refused")
    void duplicateCodeRefused() {
        StructureVersion active = activeVersion();
        when(versions.findFirstByStatus(VersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(units.existsByVersionIdAndCode(active.getId(), "ENG")).thenReturn(true);

        assertThatThrownBy(() -> service.create("ENG", "Engineering"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("code and name are trimmed, and blanks refused")
    void trimsAndRequires() {
        StructureVersion active = activeVersion();
        when(versions.findFirstByStatus(VersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(units.findByVersionIdOrderBySortOrderAscNameAsc(active.getId()))
                .thenReturn(List.of(root(active)));

        OrgUnit saved = service.create("  ENG  ", "  Engineering  ");
        assertThat(saved.getCode()).isEqualTo("ENG");
        assertThat(saved.getName()).isEqualTo("Engineering");

        assertThatThrownBy(() -> service.create("   ", "Engineering"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a department with units beneath it cannot be removed")
    void refusesToOrphanChildren() {
        UUID id = UUID.randomUUID();
        OrgUnit dep = new OrgUnit();
        dep.setId(id);
        dep.setUnitType("DEPARTMENT");
        when(units.findById(id)).thenReturn(Optional.of(dep));
        when(units.existsByParentId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("units beneath it");
        verify(units, never()).delete(any());
    }

    @Test
    @DisplayName("only department-type units are treated as departments")
    void rejectsNonDepartmentUnits() {
        UUID id = UUID.randomUUID();
        OrgUnit division = new OrgUnit();
        division.setId(id);
        division.setUnitType("DIVISION");
        when(units.findById(id)).thenReturn(Optional.of(division));

        assertThatThrownBy(() -> service.rename(id, "Anything"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a department");
    }

    private static StructureVersion activeVersion() {
        StructureVersion v = new StructureVersion();
        v.setId(UUID.randomUUID());
        v.setStatus(VersionStatus.ACTIVE);
        return v;
    }

    private static OrgUnit root(StructureVersion v) {
        OrgUnit root = new OrgUnit();
        root.setId(UUID.randomUUID());
        root.setVersionId(v.getId());
        root.setUnitType("COMPANY");
        return root;
    }
}
