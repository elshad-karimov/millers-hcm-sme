package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.lifecycle.domain.AssetCategory;
import az.millers.hcm.lifecycle.service.AssetCategoryService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M456 — Asset category CRUD (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/assets/categories")
public class AssetCategoryController {

    private final AssetCategoryService service;

    public AssetCategoryController(AssetCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetCategory> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public AssetCategory get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetCategory create(@RequestBody AssetCategory category) {
        return service.create(category);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetCategory update(@PathVariable UUID id, @RequestBody AssetCategory category) {
        return service.update(id, category);
    }
}
