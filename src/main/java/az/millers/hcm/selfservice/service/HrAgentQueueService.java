package az.millers.hcm.selfservice.service;

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

    private static final String TENANT = "default";

    private final HrAgentQueueRepository repo;

    public HrAgentQueueService(HrAgentQueueRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<HrAgentQueue> listActive() {
        return repo.findByTenantIdAndActiveTrue(TENANT)
                .stream()
                .filter(q -> TENANT.equals(q.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HrAgentQueue get(UUID id) {
        HrAgentQueue queue = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found: " + id));
        if (!TENANT.equals(queue.getTenantId())) {
            throw new ResourceNotFoundException("Queue not found: " + id);
        }
        return queue;
    }
}
