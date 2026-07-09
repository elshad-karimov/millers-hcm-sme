package az.millers.hcm.analytics.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.analytics.domain.DashboardLayout;
import az.millers.hcm.analytics.repo.DashboardLayoutRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * M474 — Dashboard layout CRUD.
 * Owner or shared read; HR_ADMIN sees all.
 */
@Service
public class DashboardLayoutService {

    private static final String TENANT = "default";

    private final DashboardLayoutRepository repository;
    private final CurrentRequest currentRequest;

    public DashboardLayoutService(DashboardLayoutRepository repository, CurrentRequest currentRequest) {
        this.repository = repository;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<DashboardLayout> listOwnedOrShared() {
        return repository.findOwnedOrShared(TENANT, currentRequest.username());
    }

    @Transactional(readOnly = true)
    public List<DashboardLayout> listAll() {
        // HR_ADMIN only - see all dashboards
        return repository.findAll().stream()
                .filter(d -> TENANT.equals(d.getTenantId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardLayout get(UUID id) {
        DashboardLayout layout = repository.findById(id)
                .filter(d -> TENANT.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard not found"));

        // Check access: owner or shared
        if (!layout.getOwnerUsername().equals(currentRequest.username()) && !layout.getShared()) {
            throw new BadRequestException("Access denied");
        }

        return layout;
    }

    @Transactional
    public DashboardLayout create(DashboardLayout layout) {
        layout.setTenantId(TENANT);
        layout.setOwnerUsername(currentRequest.username());
        return repository.save(layout);
    }

    @Transactional
    public DashboardLayout update(UUID id, DashboardLayout updates) {
        DashboardLayout existing = repository.findById(id)
                .filter(d -> TENANT.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard not found"));

        // Only owner can update
        if (!existing.getOwnerUsername().equals(currentRequest.username())) {
            throw new BadRequestException("Only owner can update dashboard");
        }

        existing.setName(updates.getName());
        existing.setShared(updates.getShared());
        existing.setWidgets(updates.getWidgets());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        DashboardLayout layout = repository.findById(id)
                .filter(d -> TENANT.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard not found"));

        // Only owner can delete
        if (!layout.getOwnerUsername().equals(currentRequest.username())) {
            throw new BadRequestException("Only owner can delete dashboard");
        }

        repository.delete(layout);
    }
}
