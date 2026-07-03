package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.BenefitCategoryDtos.CategoryRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitCategoryDtos.CategoryResponse;
import az.millers.hcm.compbenefits.service.BenefitCategoryService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Benefit category master API (HCM_11 M373). Reads open to any authenticated user
 * (categories drive dropdowns in the plan editor); writes gated to benefits/HR admin.
 */
@RestController
@RequestMapping("/api/compbenefits/benefit-categories")
public class BenefitCategoryController {

    private final BenefitCategoryService service;

    public BenefitCategoryController(BenefitCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CategoryResponse get(@PathVariable UUID id) {
        return CategoryResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest req) {
        return service.update(id, req);
    }
}
