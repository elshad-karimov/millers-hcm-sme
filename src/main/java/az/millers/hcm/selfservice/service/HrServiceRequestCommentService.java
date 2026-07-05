package az.millers.hcm.selfservice.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.domain.HrServiceRequestComment;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.repo.HrServiceRequestCommentRepository;
import az.millers.hcm.selfservice.repo.HrServiceRequestRepository;

/**
 * M437 — HR service request comment service.
 * Employees can comment on own requests (non-internal only visible to them).
 * HR can add internal notes + see all comments.
 */
@Service
public class HrServiceRequestCommentService {

    private static final String TENANT = "default";

    private final HrServiceRequestCommentRepository commentRepo;
    private final HrServiceRequestRepository requestRepo;
    private final EmployeeRepository employees;
    private final CurrentRequest currentRequest;

    public HrServiceRequestCommentService(HrServiceRequestCommentRepository commentRepo,
                                           HrServiceRequestRepository requestRepo,
                                           EmployeeRepository employees,
                                           CurrentRequest currentRequest) {
        this.commentRepo = commentRepo;
        this.requestRepo = requestRepo;
        this.employees = employees;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public HrServiceRequestComment addComment(UUID requestId, String body, boolean isInternal) {
        HrServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new BadRequestException("Request not found: " + requestId));

        // Tenant check
        if (!TENANT.equals(request.getTenantId())) {
            throw new BadRequestException("Request not found: " + requestId);
        }

        HrServiceRequestComment comment = new HrServiceRequestComment();
        comment.setTenantId(TENANT);
        comment.setRequestId(requestId);
        comment.setAuthorUsername(currentRequest.username());
        comment.setIsInternal(isInternal);
        comment.setBody(body);

        return commentRepo.save(comment);
    }

    @Transactional(readOnly = true)
    public List<HrServiceRequestComment> getCommentsForEmployee(UUID requestId, UUID employeeId) {
        // Employee sees only NON-internal comments on their own requests
        HrServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new BadRequestException("Request not found: " + requestId));

        if (!TENANT.equals(request.getTenantId())) {
            throw new BadRequestException("Request not found: " + requestId);
        }

        if (!employeeId.equals(request.getEmployeeId())) {
            throw new BadRequestException("Not authorized to view this request");
        }

        return commentRepo.findByTenantIdAndRequestIdOrderByCreatedAtAsc(TENANT, requestId)
                .stream()
                .filter(c -> TENANT.equals(c.getTenantId()))
                .filter(c -> !c.getIsInternal())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HrServiceRequestComment> getCommentsForHr(UUID requestId) {
        // HR sees all comments (including internal)
        HrServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new BadRequestException("Request not found: " + requestId));

        if (!TENANT.equals(request.getTenantId())) {
            throw new BadRequestException("Request not found: " + requestId);
        }

        // GRIEVANCE confidentiality check
        if (request.getCategory() == ServiceRequestCategory.GRIEVANCE && !currentRequest.hasRole("HR_ADMIN")) {
            throw new BadRequestException("Not authorized to view grievance request");
        }

        return commentRepo.findByTenantIdAndRequestIdOrderByCreatedAtAsc(TENANT, requestId)
                .stream()
                .filter(c -> TENANT.equals(c.getTenantId()))
                .collect(Collectors.toList());
    }
}
