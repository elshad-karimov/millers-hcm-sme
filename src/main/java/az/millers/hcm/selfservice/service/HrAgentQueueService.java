package az.millers.hcm.selfservice.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.selfservice.domain.HrAgentQueue;
import az.millers.hcm.selfservice.repo.HrAgentQueueRepository;

/**
 * M438 — HR agent queue service.
 */
@Service
public class HrAgentQueueService {


    private final HrAgentQueueRepository repo;

    public HrAgentQueueService(HrAgentQueueRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<HrAgentQueue> listActive() {
        return repo.findByTenantIdAndActiveTrue(TenantContext.current())
                .stream()
                .filter(q -> TenantContext.current().equals(q.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HrAgentQueue get(UUID id) {
        HrAgentQueue queue = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found: " + id));
        if (!TenantContext.current().equals(queue.getTenantId())) {
            throw new ResourceNotFoundException("Queue not found: " + id);
        }
        return queue;
    }
}
