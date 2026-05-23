package az.millers.hcm.audit;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.audit.repo.AuditLogRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Records immutable audit entries on sensitive entities (PRD 14.5, design principle 3).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentRequest currentRequest;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository,
                         CurrentRequest currentRequest,
                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.currentRequest = currentRequest;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String module, String entityName, String entityId,
                        String action, Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog();
        entry.setActor(currentRequest.username());
        entry.setModule(module);
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        entry.setIpAddress(currentRequest.ipAddress());
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> history(String entityName, String entityId) {
        return repository.findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"_serializationError\":\"" + ex.getOriginalMessage() + "\"}";
        }
    }
}
