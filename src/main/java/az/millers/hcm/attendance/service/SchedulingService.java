package az.millers.hcm.attendance.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.attendance.domain.OpenShift;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.ShiftSwapRequest;
import az.millers.hcm.attendance.repo.OpenShiftRepository;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ShiftSwapRequestRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M482: Open shifts + shift swap service.
 */
@Service
public class SchedulingService {

    private static final String MODULE = "attendance";

    private final OpenShiftRepository openShiftRepo;
    private final ShiftSwapRequestRepository swapRepo;
    private final RosterEntryRepository rosterRepo;
    private final EmployeeRepository employeeRepo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public SchedulingService(OpenShiftRepository openShiftRepo,
                             ShiftSwapRequestRepository swapRepo,
                             RosterEntryRepository rosterRepo,
                             EmployeeRepository employeeRepo,
                             CurrentRequest currentRequest,
                             AuditService audit,
                             NamedParameterJdbcTemplate jdbc) {
        this.openShiftRepo = openShiftRepo;
        this.swapRepo = swapRepo;
        this.rosterRepo = rosterRepo;
        this.employeeRepo = employeeRepo;
        this.currentRequest = currentRequest;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ───────────────────────────── Open Shifts ─────────────────────────────

    @Transactional(readOnly = true)
    public List<OpenShift> listOpenShifts(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return openShiftRepo.findByTenantIdAndShiftDateBetweenOrderByShiftDateAsc(TenantContext.current(), from, to);
        }
        return openShiftRepo.findByTenantIdAndStatusOrderByShiftDateAsc(TenantContext.current(), "OPEN");
    }

    @Transactional(readOnly = true)
    public OpenShift getOpenShift(UUID id) {
        return openShiftRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Open shift not found"));
    }

    @Transactional
    public OpenShift createOpenShift(OpenShift shift) {
        shift.setTenantId(TenantContext.current());
        shift.setCreatedBy(currentRequest.username());
        OpenShift saved = openShiftRepo.save(shift);
        audit.record(MODULE, "OpenShift", saved.getId().toString(), "CREATE", null,
            Map.of("shiftId", saved.getShiftId().toString(), "date", saved.getShiftDate().toString(),
                "slots", saved.getSlots()));
        return saved;
    }

    @Transactional
    public OpenShift updateOpenShift(UUID id, OpenShift updated) {
        OpenShift existing = getOpenShift(id);
        Map<String, Object> old = Map.of("slots", existing.getSlots(), "status", existing.getStatus());

        existing.setSlots(updated.getSlots());
        existing.setStatus(updated.getStatus());
        existing.setNotes(updated.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        OpenShift saved = openShiftRepo.save(existing);
        audit.record(MODULE, "OpenShift", saved.getId().toString(), "UPDATE", old,
            Map.of("slots", saved.getSlots(), "status", saved.getStatus()));
        return saved;
    }

    @Transactional
    public void claimOpenShift(UUID openShiftId, UUID employeeId) {
        OpenShift shift = getOpenShift(openShiftId);
        if (!"OPEN".equals(shift.getStatus())) {
            throw new BadRequestException("Shift is not open");
        }

        // Check if employee already has a roster entry for this date
        rosterRepo.findByEmployeeIdAndRosterDate(employeeId, shift.getShiftDate())
            .ifPresent(existing -> {
                throw new BadRequestException("Employee already has a shift on this date");
            });

        // Atomic increment with slot check (race-safe)
        String updateSql = """
            UPDATE attendance.open_shift
            SET filled = filled + 1,
                status = CASE WHEN filled + 1 >= slots THEN 'FILLED' ELSE status END,
                updated_by = :updatedBy
            WHERE id = :id
              AND tenant_id = :tenantId
              AND filled < slots
            """;

        int rowsUpdated = jdbc.update(updateSql,
            new MapSqlParameterSource()
                .addValue("id", openShiftId)
                .addValue("tenantId", TenantContext.current())
                .addValue("updatedBy", currentRequest.username()));

        if (rowsUpdated == 0) {
            throw new BadRequestException("All slots are filled");
        }

        try {
            // Create roster entry
            RosterEntry entry = new RosterEntry();
            entry.setEmployeeId(employeeId);
            entry.setShiftId(shift.getShiftId());
            entry.setRosterDate(shift.getShiftDate());
            entry.setCreatedBy(currentRequest.username());
            entry.setNotes("Claimed from open shift");
            rosterRepo.save(entry);

            // Refresh shift for audit log
            shift = getOpenShift(openShiftId);
            audit.record(MODULE, "OpenShift", shift.getId().toString(), "CLAIM", null,
                Map.of("employeeId", employeeId.toString(), "filled", shift.getFilled()));
        } catch (Exception e) {
            // Compensate: decrement filled counter if roster creation fails
            String rollbackSql = """
                UPDATE attendance.open_shift
                SET filled = GREATEST(filled - 1, 0),
                    status = CASE WHEN filled > 0 THEN 'OPEN' ELSE status END
                WHERE id = :id AND tenant_id = :tenantId
                """;
            jdbc.update(rollbackSql,
                new MapSqlParameterSource()
                    .addValue("id", openShiftId)
                    .addValue("tenantId", TenantContext.current()));
            throw e;
        }
    }

    // ───────────────────────────── Shift Swaps ─────────────────────────────

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> listSwapRequests(String status) {
        if (status != null && !status.isBlank()) {
            return swapRepo.findByTenantIdAndStatusOrderByRequestedAtDesc(TenantContext.current(), status);
        }
        return swapRepo.findByTenantIdOrderByRequestedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public ShiftSwapRequest getSwapRequest(UUID id) {
        return swapRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Swap request not found"));
    }

    @Transactional
    public ShiftSwapRequest createSwapRequest(UUID rosterEntryId, UUID fromEmployeeId, UUID toEmployeeId, String notes) {
        // IDOR guard: non-HR callers can only create swap requests FROM themselves
        boolean isHR = currentRequest.hasRole(SecurityRoles.R_SYSTEM_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_SPECIALIST);
        if (!isHR) {
            Employee currentEmp = employeeRepo.findByUsername(currentRequest.username())
                .orElseThrow(() -> new BadRequestException("User not linked to an employee record"));
            if (!currentEmp.getId().equals(fromEmployeeId)) {
                throw new BadRequestException("Cannot create swap request on behalf of another employee");
            }
        }

        RosterEntry entry = rosterRepo.findById(rosterEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));

        if (!entry.getEmployeeId().equals(fromEmployeeId)) {
            throw new BadRequestException("Roster entry does not belong to from_employee");
        }

        String requestNo = generateSwapRequestNo();
        ShiftSwapRequest request = new ShiftSwapRequest();
        request.setTenantId(TenantContext.current());
        request.setRequestNo(requestNo);
        request.setRosterEntryId(rosterEntryId);
        request.setFromEmployeeId(fromEmployeeId);
        request.setToEmployeeId(toEmployeeId);
        request.setStatus("PENDING");
        request.setNotes(notes);

        ShiftSwapRequest saved = swapRepo.save(request);
        audit.record(MODULE, "ShiftSwapRequest", saved.getId().toString(), "CREATE", null,
            Map.of("requestNo", requestNo, "fromEmployee", fromEmployeeId.toString(),
                "toEmployee", toEmployeeId.toString()));
        return saved;
    }

    @Transactional
    public void approveSwapRequest(UUID requestId, String approverUsername) {
        ShiftSwapRequest request = getSwapRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Request is not pending");
        }

        // Self-approve block: resolve approver's employee ID from username
        UUID approverEmployeeId = getEmployeeIdByUsername(approverUsername);
        if (approverEmployeeId != null &&
            (approverEmployeeId.equals(request.getFromEmployeeId()) || approverEmployeeId.equals(request.getToEmployeeId()))) {
            throw new BadRequestException("Cannot self-approve swap request");
        }

        RosterEntry entry = rosterRepo.findById(request.getRosterEntryId())
            .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));

        UUID oldEmployeeId = entry.getEmployeeId();
        UUID newEmployeeId = request.getToEmployeeId();

        // Swap the employee assignment
        entry.setEmployeeId(newEmployeeId);
        entry.setUpdatedBy(approverUsername);
        rosterRepo.save(entry);

        request.setStatus("APPROVED");
        request.setApprovedAt(Instant.now());
        request.setApprovedBy(approverUsername);
        swapRepo.save(request);

        audit.record(MODULE, "ShiftSwapRequest", request.getId().toString(), "APPROVE",
            Map.of("status", "PENDING", "employeeId", oldEmployeeId.toString()),
            Map.of("status", "APPROVED", "employeeId", newEmployeeId.toString(), "approvedBy", approverUsername));
    }

    @Transactional
    public void rejectSwapRequest(UUID requestId, String reason) {
        ShiftSwapRequest request = getSwapRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Request is not pending");
        }

        request.setStatus("REJECTED");
        request.setRejectionReason(reason);
        swapRepo.save(request);

        audit.record(MODULE, "ShiftSwapRequest", request.getId().toString(), "REJECT",
            Map.of("status", "PENDING"),
            Map.of("status", "REJECTED", "reason", reason));
    }

    private String generateSwapRequestNo() {
        Long seq = jdbc.queryForObject(
            "SELECT nextval('attendance.shift_swap_seq')",
            new MapSqlParameterSource(),
            Long.class
        );
        return BusinessNumbers.format("SWP", 5, seq);
    }

    private String getEmployeeName(UUID employeeId) {
        try {
            return jdbc.queryForObject(
                "SELECT CONCAT(first_name, ' ', last_name) FROM core_hr.employee WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource()
                    .addValue("id", employeeId)
                    .addValue("tenantId", TenantContext.current()),
                String.class
            );
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private UUID getEmployeeIdByUsername(String username) {
        try {
            return jdbc.queryForObject(
                "SELECT id FROM core_hr.employee WHERE username = :username AND tenant_id = :tenantId",
                new MapSqlParameterSource()
                    .addValue("username", username)
                    .addValue("tenantId", TenantContext.current()),
                UUID.class
            );
        } catch (Exception e) {
            return null;
        }
    }
}
