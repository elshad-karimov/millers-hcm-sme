package az.millers.hcm.selfservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.domain.HrServiceRequestComment;
import az.millers.hcm.selfservice.service.HrServiceRequestCommentService;

/**
 * M437 — HR service request comment endpoints.
 * Employees can comment on own requests; HR can add internal notes + view all.
 */
@RestController
@RequestMapping("/api")
public class HrServiceRequestCommentController {

    private final HrServiceRequestCommentService service;
    private final EmployeeRepository employees;
    private final CurrentRequest currentRequest;

    public HrServiceRequestCommentController(HrServiceRequestCommentService service,
                                              EmployeeRepository employees,
                                              CurrentRequest currentRequest) {
        this.service = service;
        this.employees = employees;
        this.currentRequest = currentRequest;
    }

    // ── Employee endpoints ──

    @PostMapping("/self/hr-requests/{requestId}/comments")
    @PreAuthorize("isAuthenticated()")
    public HrServiceRequestComment addCommentAsEmployee(@PathVariable UUID requestId,
                                                          @RequestBody AddCommentRequest request) {
        // Employees can only add NON-internal comments
        return service.addComment(requestId, request.body, false);
    }

    @GetMapping("/self/hr-requests/{requestId}/comments")
    @PreAuthorize("isAuthenticated()")
    public List<HrServiceRequestComment> getCommentsAsEmployee(@PathVariable UUID requestId) {
        Employee emp = employees.findByUsername(currentRequest.username())
                .orElseThrow(() -> new BadRequestException("Employee not found for username: " + currentRequest.username()));
        return service.getCommentsForEmployee(requestId, emp.getId());
    }

    // ── HR endpoints ──

    @PostMapping("/hr/service-requests/{requestId}/comments")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public HrServiceRequestComment addCommentAsHr(@PathVariable UUID requestId,
                                                   @RequestBody AddCommentRequest request) {
        // HR can add internal or non-internal comments
        return service.addComment(requestId, request.body, request.isInternal != null ? request.isInternal : false);
    }

    @GetMapping("/hr/service-requests/{requestId}/comments")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<HrServiceRequestComment> getCommentsAsHr(@PathVariable UUID requestId) {
        return service.getCommentsForHr(requestId);
    }

    record AddCommentRequest(String body, Boolean isInternal) {}
}
