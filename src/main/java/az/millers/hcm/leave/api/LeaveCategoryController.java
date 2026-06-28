package az.millers.hcm.leave.api;

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

import az.millers.hcm.leave.domain.LeaveCategory;
import az.millers.hcm.leave.service.LeaveCategoryService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/categories")
public class LeaveCategoryController {

    private final LeaveCategoryService service;

    public LeaveCategoryController(LeaveCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LeaveCategory> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveCategory create(@RequestBody LeaveCategory req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveCategory update(@PathVariable UUID id, @RequestBody LeaveCategory req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void toggleActive(@PathVariable UUID id) {
        service.toggleActive(id);
    }
}
