package az.millers.hcm.workflow.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.workflow.api.dto.ActionRequest;
import az.millers.hcm.workflow.api.dto.WorkflowActionResponse;
import az.millers.hcm.workflow.api.dto.WorkflowDefinitionResponse;
import az.millers.hcm.workflow.api.dto.WorkflowInstanceResponse;
import az.millers.hcm.workflow.service.WorkflowService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping("/definitions")
    public List<WorkflowDefinitionResponse> definitions() {
        return service.listDefinitions().stream()
                .map(d -> WorkflowDefinitionResponse.from(d, service.stepsFor(d.getId())))
                .toList();
    }

    @GetMapping("/inbox")
    public List<WorkflowInstanceResponse> inbox(Authentication authentication) {
        List<String> myRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        return service.inboxFor(myRoles).stream()
                .map(WorkflowInstanceResponse::from).toList();
    }

    @GetMapping("/initiated")
    public List<WorkflowInstanceResponse> initiated(Authentication authentication) {
        return service.initiatedBy(authentication.getName()).stream()
                .map(WorkflowInstanceResponse::from).toList();
    }

    @GetMapping("/instances/{id}")
    public WorkflowInstanceResponse get(@PathVariable UUID id) {
        return WorkflowInstanceResponse.from(service.get(id));
    }

    @GetMapping("/instances/{id}/history")
    public List<WorkflowActionResponse> history(@PathVariable UUID id) {
        return service.history(id).stream().map(WorkflowActionResponse::from).toList();
    }

    @GetMapping("/instances")
    public List<WorkflowInstanceResponse> bySubject(
            @RequestParam String module,
            @RequestParam String entity,
            @RequestParam String id) {
        return service.findForSubject(module, entity, id).stream()
                .map(WorkflowInstanceResponse::from).toList();
    }

    @PostMapping("/instances/{id}/actions")
    public WorkflowInstanceResponse act(@PathVariable UUID id,
                                         @Valid @RequestBody ActionRequest req) {
        return WorkflowInstanceResponse.from(service.act(id, req));
    }
}
