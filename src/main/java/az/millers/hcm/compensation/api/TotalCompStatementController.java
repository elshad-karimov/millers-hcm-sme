package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.TotalCompStatement;
import az.millers.hcm.compensation.service.TotalCompStatementService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M368 — Total Compensation Statement API.
 */
@RestController
@RequestMapping("/api/compensation/total-comp-statements")
public class TotalCompStatementController {

    private final TotalCompStatementService service;
    private final EmployeeContextService employeeContext;

    public TotalCompStatementController(TotalCompStatementService service,
                                         EmployeeContextService employeeContext) {
        this.service = service;
        this.employeeContext = employeeContext;
    }

    @PostMapping("/generate")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public TotalCompStatement generate(@RequestParam UUID employeeId, @RequestParam int year) {
        return service.generate(employeeId, year);
    }

    @PostMapping("/generate-all")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public Map<String, Object> generateAll(@RequestParam int year) {
        return service.generateAll(year);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<TotalCompStatement> list(@RequestParam int year) {
        return service.list(year);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public TotalCompStatement release(@PathVariable UUID id) {
        return service.release(id);
    }

    @PostMapping("/release-all")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public Map<String, Object> releaseAll(@RequestParam int year) {
        return service.releaseAll(year);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        byte[] pdf = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"TotalCompStatement.pdf\"")
                .body(pdf);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Employee Self-Service
    // ══════════════════════════════════════════════════════════════════════════════

    @GetMapping("/employees/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TotalCompStatement getMy(@RequestParam int year) {
        Employee emp = employeeContext.currentEmployee();

        List<TotalCompStatement> stmts = service.list(year);
        TotalCompStatement stmt = stmts.stream()
                .filter(s -> s.getEmployeeId().equals(emp.getId()))
                .filter(s -> "RELEASED".equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No released statement for year: " + year));

        return stmt;
    }

    @GetMapping("/employees/me/download")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<byte[]> downloadMy(@RequestParam int year) {
        Employee emp = employeeContext.currentEmployee();

        List<TotalCompStatement> stmts = service.list(year);
        TotalCompStatement stmt = stmts.stream()
                .filter(s -> s.getEmployeeId().equals(emp.getId()))
                .filter(s -> "RELEASED".equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No released statement for year: " + year));

        byte[] pdf = service.download(stmt.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"MyTotalCompStatement.pdf\"")
                .body(pdf);
    }
}
