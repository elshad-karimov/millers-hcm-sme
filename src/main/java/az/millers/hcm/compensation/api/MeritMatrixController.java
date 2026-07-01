package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.MeritMatrixCellDto;
import az.millers.hcm.compensation.api.dto.MeritMatrixDto;
import az.millers.hcm.compensation.domain.MeritMatrix;
import az.millers.hcm.compensation.domain.MeritMatrixCell;
import az.millers.hcm.compensation.service.MeritMatrixService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M363 — Merit matrix endpoints.
 */
@RestController
@RequestMapping("/api/compensation/merit-matrices")
public class MeritMatrixController {

    private final MeritMatrixService service;

    public MeritMatrixController(MeritMatrixService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<MeritMatrixDto> list() {
        return service.listActive().stream()
                .map(MeritMatrixDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public MeritMatrixDto get(@PathVariable UUID id) {
        MeritMatrix matrix = service.get(id);
        List<MeritMatrixCell> cells = service.getCells(id);
        return MeritMatrixDto.fromWithCells(matrix, cells);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.CREATED)
    public MeritMatrixDto create(@RequestBody MeritMatrixDto req) {
        MeritMatrix matrix = service.create(req.code(), req.name());
        return MeritMatrixDto.from(matrix);
    }

    @PutMapping("/{id}/cells")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public MeritMatrixDto updateCells(@PathVariable UUID id, @RequestBody List<MeritMatrixCellDto> cells) {
        List<MeritMatrixCell> entities = cells.stream()
                .map(MeritMatrixCellDto::toEntity)
                .toList();
        service.updateCells(id, entities);
        MeritMatrix matrix = service.get(id);
        List<MeritMatrixCell> updated = service.getCells(id);
        return MeritMatrixDto.fromWithCells(matrix, updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/suggest")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public Map<String, Object> suggest(@RequestParam UUID employeeId,
                                        @RequestParam(required = false) UUID matrixId) {
        return service.suggest(employeeId, matrixId);
    }
}
