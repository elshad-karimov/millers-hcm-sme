package az.millers.hcm.attendance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.DailySummaryResponse;
import az.millers.hcm.attendance.api.dto.EngineRunResponse;
import az.millers.hcm.attendance.api.dto.RunEngineRequest;
import az.millers.hcm.attendance.api.dto.SummaryCorrectionRequest;
import az.millers.hcm.attendance.service.AttendanceEngine;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance/summary")
public class AttendanceSummaryController {

    private final AttendanceEngine engine;

    public AttendanceSummaryController(AttendanceEngine engine) {
        this.engine = engine;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public List<DailySummaryResponse> list(
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam(required = false) UUID employeeId) {
        var rows = employeeId != null
                ? engine.listForEmployee(employeeId, fromDate, toDate)
                : engine.listByRange(fromDate, toDate);
        return rows.stream().map(DailySummaryResponse::from).toList();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public EngineRunResponse run(@Valid @RequestBody RunEngineRequest req) {
        var r = engine.run(req.fromDate(), req.toDate(), req.employeeId());
        return new EngineRunResponse(r.employeesProcessed(), r.summariesWritten());
    }

    @PostMapping("/{id}/correct")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public DailySummaryResponse correct(@PathVariable UUID id,
                                         @Valid @RequestBody SummaryCorrectionRequest req) {
        return DailySummaryResponse.from(engine.applyCorrection(id, req));
    }
}
