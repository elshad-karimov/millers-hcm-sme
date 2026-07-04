package az.millers.hcm.selfservice.team;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.selfservice.team.TeamDtos.TeamMember;
import az.millers.hcm.selfservice.team.TeamDtos.TeamSummary;

/**
 * Manager self-service endpoints (M76 / P2-11/12). Sits under
 * {@code /api/self/team} so it composes with the existing
 * {@link az.millers.hcm.selfservice.api.SelfController} URL family without
 * cross-controller wiring.
 *
 * <p>Both endpoints rely on the caller's authenticated user — they don't
 * accept a manager-id parameter. To inspect another manager's team, use
 * the regular {@code /api/employees} surface with HR_ADMIN credentials.
 */
@RestController
@RequestMapping("/api/self/team")
@PreAuthorize("isAuthenticated()")
public class ManagerTeamController {

    private final ManagerTeamService service;

    public ManagerTeamController(ManagerTeamService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamMember> directReports() {
        return service.directReports();
    }

    @GetMapping("/summary")
    public TeamSummary summary() {
        return service.summary();
    }

    /**
     * M433 — Team compensation view: requires manager_can_view_salary setting.
     * Returns current salaries for direct reports only.
     */
    @GetMapping("/compensation")
    public List<az.millers.hcm.selfservice.team.TeamDtos.TeamCompensation> compensation() {
        return service.teamCompensation();
    }
}
