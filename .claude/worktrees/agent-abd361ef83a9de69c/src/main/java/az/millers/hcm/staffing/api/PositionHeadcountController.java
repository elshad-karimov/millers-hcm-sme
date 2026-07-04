package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionHeadcountDtos.HeadcountSummary;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.service.PositionHeadcountService;

/**
 * Position control dashboard endpoints (M109).
 *
 * <ul>
 *   <li>{@code GET /staffing/headcount} — per-position roll-up, per-org-unit
 *       roll-up, totals. HR + managers can read.</li>
 *   <li>{@code POST /staffing/headcount/reconcile} — force a recompute of
 *       {@code Position.occupiedHeadcount} from the ground-truth employee
 *       table. HR_ADMIN only — it audits every drift fix.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/staffing/headcount")
public class PositionHeadcountController {

    private final PositionHeadcountService service;

    public PositionHeadcountController(PositionHeadcountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public HeadcountSummary summary() {
        return service.report();
    }

    @PostMapping("/reconcile")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public Map<String, Object> reconcile() {
        List<Position> drifted = service.reconcile();
        return Map.of(
                "driftedCount", drifted.size(),
                "drifted", drifted.stream().map(p -> Map.of(
                        "id", p.getId(),
                        "code", p.getCode(),
                        "title", p.getTitle(),
                        "occupiedHeadcount", p.getOccupiedHeadcount())).toList());
    }
}
