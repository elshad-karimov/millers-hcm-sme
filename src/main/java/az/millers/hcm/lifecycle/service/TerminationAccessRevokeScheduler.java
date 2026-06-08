package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.repo.TerminationRequestRepository;

/**
 * Disables Keycloak accounts for terminated employees at EOD of their
 * termination effective date (M185 / PRD §8.11.6 AC:
 * "system access is disabled at end-of-day on the effective date").
 *
 * <p>Runs at 20:00 UTC daily. Finds all PROCESSED terminations where:
 * <ul>
 *   <li>{@code effectiveDate ≤ today}</li>
 *   <li>{@code systemAccessRevokedAt IS NULL}</li>
 * </ul>
 * For each, resolves the employee username from the employee record and
 * calls {@link KeycloakAdminService#disableUser(String)}. Records the
 * revocation timestamp on the termination row.
 *
 * <p>Each employee is processed individually; a failure on one does not
 * block the others. If Keycloak is unreachable the row keeps
 * {@code systemAccessRevokedAt = NULL} and the scheduler will retry next run.
 */
@Component
public class TerminationAccessRevokeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TerminationAccessRevokeScheduler.class);

    private final TerminationRequestRepository terminations;
    private final EmployeeRepository employees;
    private final KeycloakAdminService keycloak;

    public TerminationAccessRevokeScheduler(TerminationRequestRepository terminations,
                                             EmployeeRepository employees,
                                             KeycloakAdminService keycloak) {
        this.terminations = terminations;
        this.employees = employees;
        this.keycloak = keycloak;
    }

    @Scheduled(cron = "0 0 20 * * *")   // 20:00 UTC daily
    @Transactional
    public void revokeAccessForTerminated() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<TerminationRequest> due = terminations.findPendingAccessRevocation(today);

        if (due.isEmpty()) return;

        log.info("TerminationAccessRevokeScheduler: {} account(s) pending revocation for {}",
                due.size(), today);

        int revoked = 0;
        int failed  = 0;
        for (TerminationRequest t : due) {
            try {
                Employee emp = employees.findById(t.getEmployeeId()).orElse(null);
                if (emp == null) {
                    log.warn("TerminationAccessRevokeScheduler: employee {} not found for {}",
                            t.getEmployeeId(), t.getTerminationNo());
                    t.setSystemAccessRevokedAt(OffsetDateTime.now());   // skip — no employee record
                    terminations.save(t);
                    revoked++;
                    continue;
                }

                String username = emp.getUsername();
                if (username == null || username.isBlank()) {
                    log.debug("TerminationAccessRevokeScheduler: employee {} has no username — marking revoked",
                            t.getEmployeeId());
                } else {
                    keycloak.disableUser(username);
                }

                t.setSystemAccessRevokedAt(OffsetDateTime.now());
                terminations.save(t);
                revoked++;

            } catch (Exception ex) {
                failed++;
                log.warn("TerminationAccessRevokeScheduler: failed to revoke access for {} ({}): {}",
                        t.getTerminationNo(), t.getEmployeeId(), ex.getMessage());
            }
        }

        log.info("TerminationAccessRevokeScheduler: revoked={} failed={}", revoked, failed);
    }
}
