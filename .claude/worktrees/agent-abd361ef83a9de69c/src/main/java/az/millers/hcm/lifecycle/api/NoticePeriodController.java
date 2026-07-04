package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.lifecycle.domain.NoticePeriodRule;
import az.millers.hcm.lifecycle.service.NoticePeriodService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/lifecycle/offboarding/notice-period-rules")
public class NoticePeriodController {

    private final NoticePeriodService service;

    public NoticePeriodController(NoticePeriodService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<NoticePeriodRuleDto> list() {
        return service.listActive().stream().map(NoticePeriodRuleDto::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public NoticePeriodRuleDto get(@PathVariable UUID id) {
        return NoticePeriodRuleDto.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public NoticePeriodRuleDto create(@Valid @RequestBody UpsertRequest req) {
        NoticePeriodRule rule = new NoticePeriodRule();
        applyRequest(rule, req);
        return NoticePeriodRuleDto.from(service.upsert(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public NoticePeriodRuleDto update(@PathVariable UUID id, @Valid @RequestBody UpsertRequest req) {
        NoticePeriodRule rule = service.get(id);
        applyRequest(rule, req);
        return NoticePeriodRuleDto.from(service.upsert(rule));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    private static void applyRequest(NoticePeriodRule rule, UpsertRequest req) {
        rule.setEmploymentType(req.employmentType());
        rule.setOnProbation(req.onProbation() != null && req.onProbation());
        rule.setNoticeDays(req.noticeDays());
        rule.setDescription(req.description());
        rule.setActive(true);
    }

    public record NoticePeriodRuleDto(
            UUID id,
            EmploymentType employmentType,
            boolean onProbation,
            int noticeDays,
            String description,
            boolean active) {

        static NoticePeriodRuleDto from(NoticePeriodRule r) {
            return new NoticePeriodRuleDto(r.getId(), r.getEmploymentType(), r.isOnProbation(),
                    r.getNoticeDays(), r.getDescription(), r.isActive());
        }
    }

    public record UpsertRequest(
            EmploymentType employmentType,
            Boolean onProbation,
            @NotNull @Min(0) Integer noticeDays,
            String description) {}
}
