package az.millers.hcm.attendance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionRequest;
import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionResponse;
import az.millers.hcm.attendance.api.dto.DailySummaryResponse;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeRequestDto;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeResponse;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.service.AttendanceCorrectionService;
import az.millers.hcm.attendance.service.OvertimeRequestService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M334: ESS/MSS attendance views.
 */
@RestController
public class AttendanceSelfServiceController {

    private final DailySummaryRepository summaryRepository;
    private final AttendanceCorrectionService correctionService;
    private final OvertimeRequestService overtimeService;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public AttendanceSelfServiceController(DailySummaryRepository summaryRepository,
                                            AttendanceCorrectionService correctionService,
                                            OvertimeRequestService overtimeService,
                                            EmployeeContextService employeeContext,
                                            CurrentRequest currentRequest) {
        this.summaryRepository = summaryRepository;
        this.correctionService = correctionService;
        this.overtimeService = overtimeService;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    @GetMapping("/api/self/attendance/summaries")
    @PreAuthorize("isAuthenticated()")
    public List<DailySummaryResponse> getSelfSummaries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID employeeId = employeeContext.currentEmployee().getId();
        List<DailySummary> summaries = summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
                employeeId, from, to);
        return summaries.stream()
                .map(DailySummaryResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/self/attendance/corrections")
    @PreAuthorize("isAuthenticated()")
    public List<CorrectionResponse> getSelfCorrections() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        return correctionService.listByEmployee(employeeId);
    }

    @PostMapping("/api/self/attendance/corrections")
    @PreAuthorize("isAuthenticated()")
    public CorrectionResponse submitSelfCorrection(@RequestBody CorrectionRequest request) {
        return correctionService.submit(request, currentRequest.username());
    }

    @GetMapping("/api/self/attendance/overtime-requests")
    @PreAuthorize("isAuthenticated()")
    public List<OvertimeResponse> getSelfOvertimeRequests() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        return overtimeService.listByEmployee(employeeId);
    }

    @PostMapping("/api/self/attendance/overtime-requests")
    @PreAuthorize("isAuthenticated()")
    public OvertimeResponse submitSelfOvertimeRequest(@RequestBody OvertimeRequestDto request) {
        return overtimeService.submit(request, currentRequest.username());
    }

    @GetMapping("/api/manager/attendance/summaries")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<DailySummaryResponse> getManagerSummaries(
            @RequestParam UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DailySummary> summaries = summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
                employeeId, from, to);
        return summaries.stream()
                .map(DailySummaryResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/manager/attendance/corrections")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<CorrectionResponse> getPendingCorrections() {
        return correctionService.list().stream()
                .filter(c -> "PENDING".equals(c.workflowStatus()))
                .collect(Collectors.toList());
    }

    @PostMapping("/api/manager/attendance/corrections/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public CorrectionResponse managerApprove(@PathVariable UUID id,
                                               @RequestBody(required = false) String comment) {
        return correctionService.decide(id, "APPROVED", comment, currentRequest.username());
    }

    @PostMapping("/api/manager/attendance/corrections/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public CorrectionResponse managerReject(@PathVariable UUID id,
                                              @RequestBody(required = false) String comment) {
        return correctionService.decide(id, "REJECTED", comment, currentRequest.username());
    }
}
