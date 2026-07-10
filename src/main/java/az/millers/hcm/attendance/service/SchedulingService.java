package az.millers.hcm.attendance.service;

import az.millers.hcm.attendance.domain.OpenShift;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.ShiftSwapRequest;
import az.millers.hcm.attendance.repo.OpenShiftRepository;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ShiftSwapRequestRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
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

/**
 * M482: Open shifts + shift swap service.
 */
@Service
public class SchedulingService {

    private static final String MODULE = "attendance";
    private static final String TENANT_ID = "default";

    private final OpenShiftRepository openShiftRepo;
    private final ShiftSwapRequestRepository swapRepo;
    private final RosterEntryRepository rosterRepo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public SchedulingService(OpenShiftRepository openShiftRepo,
                             ShiftSwapRequestRepository swapRepo,
                             RosterEntryRepository rosterRepo,
                             CurrentRequest currentRequest,
                             AuditService audit,
                             NamedParameterJdbcTemplate jdbc) {
        this.openShiftRepo = openShiftRepo;
        this.swapRepo = swapRepo;
        this.rosterRepo = rosterRepo;
        this.currentRequest = currentRequest;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ───────────────────────────── Open Shifts ─────────────────────────────

    @Transactional(readOnly = true)
    public List<OpenShift> listOpenShifts(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return openShiftRepo.findByTenantIdAndShiftDateBetweenOrderByShiftDateAsc(TENANT_ID, from, to);
        }
        return openShiftRepo.findByTenantIdAndStatusOrderByShiftDateAsc(TENANT_ID, "OPEN");
    }

    @Transactional(readOnly = true)
    public OpenShift getOpenShift(UUID id) {
        return openShiftRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Open shift not found"));
    }

    @Transactional
    public OpenShift createOpenShift(OpenShift shift) {
        shift.setTenantId(TENANT_ID);
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
        if (shift.getFilled() >= shift.getSlots()) {
            throw new BadRequestException("All slots are filled");
        }

        // Check if employee already has a roster entry for this date
        rosterRepo.findByEmployeeIdAndRosterDate(employeeId, shift.getShiftDate())
            .ifPresent(existing -> {
                throw new BadRequestException("Employee already has a shift on this date");
            });

        // Create roster entry
        RosterEntry entry = new RosterEntry();
        entry.setEmployeeId(employeeId);
        entry.setShiftId(shift.getShiftId());
        entry.setRosterDate(shift.getShiftDate());
        entry.setCreatedBy(currentRequest.username());
        entry.setNotes("Claimed from open shift");
        rosterRepo.save(entry);

        // Increment filled counter
        shift.setFilled(shift.getFilled() + 1);
        if (shift.getFilled() >= shift.getSlots()) {
            shift.setStatus("FILLED");
        }
        shift.setUpdatedBy(currentRequest.username());
        openShiftRepo.save(shift);

        audit.record(MODULE, "OpenShift", shift.getId().toString(), "CLAIM", null,
            Map.of("employeeId", employeeId.toString(), "filled", shift.getFilled()));
    }

    // ───────────────────────────── Shift Swaps ─────────────────────────────

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> listSwapRequests(String status) {
        if (status != null && !status.isBlank()) {
            return swapRepo.findByTenantIdAndStatusOrderByRequestedAtDesc(TENANT_ID, status);
        }
        return swapRepo.findByTenantIdOrderByRequestedAtDesc(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public ShiftSwapRequest getSwapRequest(UUID id) {
        return swapRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Swap request not found"));
    }

    @Transactional
    public ShiftSwapRequest createSwapRequest(UUID rosterEntryId, UUID fromEmployeeId, UUID toEmployeeId, String notes) {
        RosterEntry entry = rosterRepo.findById(rosterEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));

        if (!entry.getEmployeeId().equals(fromEmployeeId)) {
            throw new BadRequestException("Roster entry does not belong to from_employee");
        }

        String requestNo = generateSwapRequestNo();
        ShiftSwapRequest request = new ShiftSwapRequest();
        request.setTenantId(TENANT_ID);
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

        // Self-approve block
        String fromEmployeeName = getEmployeeName(request.getFromEmployeeId());
        String toEmployeeName = getEmployeeName(request.getToEmployeeId());
        if (approverUsername.equalsIgnoreCase(fromEmployeeName) || approverUsername.equalsIgnoreCase(toEmployeeName)) {
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
        return String.format("SWP-%05d", seq);
    }

    private String getEmployeeName(UUID employeeId) {
        try {
            return jdbc.queryForObject(
                "SELECT CONCAT(first_name, ' ', last_name) FROM core_hr.employee WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource()
                    .addValue("id", employeeId)
                    .addValue("tenantId", TENANT_ID),
                String.class
            );
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
