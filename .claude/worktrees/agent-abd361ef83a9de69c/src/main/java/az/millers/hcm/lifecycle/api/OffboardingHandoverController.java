package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.HandoverTaskCreateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.HandoverTaskResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.HandoverTaskUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingCaseResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ReplacementUpdateRequest;
import az.millers.hcm.lifecycle.service.OffboardingHandoverService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lifecycle/offboarding")
@RequiredArgsConstructor
public class OffboardingHandoverController {

    private final OffboardingHandoverService handoverService;

    @GetMapping("/cases/{caseId}/handover-tasks")
    @Secured("ROLE_READ_HR")
    public List<HandoverTaskResponse> listTasks(@PathVariable UUID caseId) {
        return handoverService.getTasks(caseId).stream()
                .map(HandoverTaskResponse::from).toList();
    }

    @PostMapping("/cases/{caseId}/handover-tasks")
    @Secured("ROLE_WRITE_HR")
    public HandoverTaskResponse createTask(@PathVariable UUID caseId,
                                            @RequestBody HandoverTaskCreateRequest req) {
        return HandoverTaskResponse.from(handoverService.createTask(caseId, req));
    }

    @PatchMapping("/handover-tasks/{taskId}")
    @Secured("ROLE_WRITE_HR")
    public HandoverTaskResponse updateTask(@PathVariable UUID taskId,
                                            @RequestBody HandoverTaskUpdateRequest req) {
        return HandoverTaskResponse.from(handoverService.updateTask(taskId, req));
    }

    @PatchMapping("/cases/{caseId}/replacement")
    @Secured("ROLE_WRITE_HR")
    public ResponseEntity<OffboardingCaseResponse> updateReplacement(
            @PathVariable UUID caseId,
            @RequestBody ReplacementUpdateRequest req) {
        return ResponseEntity.ok(
                OffboardingCaseResponse.from(handoverService.updateReplacement(caseId, req)));
    }
}
