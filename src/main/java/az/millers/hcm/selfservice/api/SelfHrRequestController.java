package az.millers.hcm.selfservice.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.selfservice.api.dto.HrServiceRequestDto;
import az.millers.hcm.selfservice.api.dto.SubmitServiceRequestDto;
import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.selfservice.service.HrServiceRequestService;

/**
 * M429 — ESS: employee submits + views own HR service requests.
 */
@RestController
@RequestMapping("/api/self/hr-requests")
public class SelfHrRequestController {

    private final HrServiceRequestService service;
    private final EmployeeContextService employeeContext;

    public SelfHrRequestController(HrServiceRequestService service,
                                    EmployeeContextService employeeContext) {
        this.service = service;
        this.employeeContext = employeeContext;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto submit(@RequestBody SubmitServiceRequestDto dto) {
        Employee emp = employeeContext.currentEmployee();
        HrServiceRequest req = service.submit(
                dto.category(),
                dto.priority(),
                dto.subject(),
                dto.description(),
                emp.getId()
        );
        return HrServiceRequestDto.from(req);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR_ADMIN', 'HR_SPECIALIST')")
    public List<HrServiceRequestDto> myRequests() {
        Employee emp = employeeContext.currentEmployee();
        return service.myRequests(emp.getId()).stream()
                .map(HrServiceRequestDto::from)
                .toList();
    }
}
