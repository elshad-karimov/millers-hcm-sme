package az.millers.hcm.corehr.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestPriority;
import az.millers.hcm.selfservice.domain.ServiceRequestStatus;
import az.millers.hcm.selfservice.repo.HrServiceRequestRepository;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M441 — Document expiry → renewal request auto-creation.
 * For employee_document rows whose category has auto_create_renewal_request=true
 * and expiry_date within 30 days, auto-creates an HR service request.
 */
@Service
@Transactional
public class DocumentExpiryRenewalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryRenewalService.class);
    private static final String MODULE = "core-hr";
    private static final String ENTITY = "EmployeeDocument";
    private static final int EXPIRY_WARNING_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final HrServiceRequestRepository hrRequestRepo;
    private final EmployeeRepository employeeRepo;
    private final AuditService audit;

    public DocumentExpiryRenewalService(NamedParameterJdbcTemplate jdbc,
                                       HrServiceRequestRepository hrRequestRepo,
                                       EmployeeRepository employeeRepo,
                                       AuditService audit) {
        this.jdbc = jdbc;
        this.hrRequestRepo = hrRequestRepo;
        this.employeeRepo = employeeRepo;
        this.audit = audit;
    }

    public record ExpiringDocumentDTO(
            UUID documentId,
            UUID employeeId,
            String employeeName,
            String categoryName,
            String title,
            LocalDate expiryDate) {}

    /**
     * Daily scan at 06:30 — find expiring documents and auto-create renewal requests.
     */
    @Scheduled(cron = "${hcm.document-expiry.cron:0 30 6 * * *}")
    public void scanExpiringDocuments() {
        LocalDate threshold = LocalDate.now().plusDays(EXPIRY_WARNING_DAYS);
        List<ExpiringDocumentDTO> expiring = findExpiringDocuments(threshold);

        int created = 0;
        for (ExpiringDocumentDTO doc : expiring) {
            if (createRenewalRequestIfNeeded(doc)) {
                created++;
            }
        }

        if (created > 0) {
            log.info("DocumentExpiryRenewalService: created {} renewal requests for {} expiring documents",
                    created, expiring.size());
        }
    }

    /**
     * Find employee documents that expire within threshold and have auto_create_renewal_request enabled.
     */
    private List<ExpiringDocumentDTO> findExpiringDocuments(LocalDate threshold) {
        String sql = """
            SELECT
                ed.id AS document_id,
                ed.employee_id,
                e.first_name || ' ' || e.last_name AS employee_name,
                dc.name AS category_name,
                ed.title,
                ed.expiry_date
            FROM core_hr.employee_document ed
            JOIN core_hr.document_category dc ON ed.category_id = dc.id
            JOIN core_hr.employee e ON ed.employee_id = e.id
            WHERE ed.tenant_id = :tenantId
              AND dc.auto_create_renewal_request = true
              AND ed.expiry_date IS NOT NULL
              AND ed.expiry_date <= :threshold
              AND ed.expiry_date >= CURRENT_DATE
              AND ed.version = (
                  SELECT MAX(ed2.version)
                  FROM core_hr.employee_document ed2
                  WHERE ed2.employee_id = ed.employee_id
                    AND ed2.category_id = ed.category_id
                    AND ed2.tenant_id = :tenantId
              )
            ORDER BY ed.expiry_date
            """;

        return jdbc.query(sql,
                Map.of("tenantId", TenantContext.current(), "threshold", threshold),
                (rs, rowNum) -> new ExpiringDocumentDTO(
                        UUID.fromString(rs.getString("document_id")),
                        UUID.fromString(rs.getString("employee_id")),
                        rs.getString("employee_name"),
                        rs.getString("category_name"),
                        rs.getString("title"),
                        rs.getDate("expiry_date").toLocalDate()));
    }

    /**
     * Create renewal request if one doesn't already exist for this document.
     * Returns true if created, false if already exists.
     */
    private boolean createRenewalRequestIfNeeded(ExpiringDocumentDTO doc) {
        // Check if an OPEN/IN_PROGRESS renewal request already exists for this document
        boolean exists = hrRequestRepo.existsByCategoryAndSourceRefAndStatusIn(
                ServiceRequestCategory.DOCUMENT_RENEWAL,
                doc.documentId(),
                List.of(ServiceRequestStatus.OPEN, ServiceRequestStatus.IN_PROGRESS));

        if (exists) {
            return false;
        }

        // Create the renewal request
        HrServiceRequest request = new HrServiceRequest();
        request.setId(UUID.randomUUID());
        request.setTenantId(TenantContext.current());
        request.setRequestNo(generateRequestNo());
        request.setEmployeeId(doc.employeeId());
        request.setCategory(ServiceRequestCategory.DOCUMENT_RENEWAL);
        request.setPriority(ServiceRequestPriority.HIGH);
        request.setSubject("Document renewal required: " + doc.categoryName());
        request.setDescription(String.format(
                "Document '%s' (%s) for %s expires on %s. Please renew this document.",
                doc.title() != null ? doc.title() : doc.categoryName(),
                doc.categoryName(),
                doc.employeeName(),
                doc.expiryDate()));
        request.setStatus(ServiceRequestStatus.OPEN);
        request.setSourceRef(doc.documentId());
        request.setCreatedAt(OffsetDateTime.now());
        request.setCreatedBy("system");
        request.setUpdatedAt(OffsetDateTime.now());
        request.setUpdatedBy("system");

        hrRequestRepo.save(request);

        audit.record(MODULE, ENTITY, doc.documentId().toString(),
                "RENEWAL_REQUEST_CREATED", null,
                Map.of(
                        "requestNo", request.getRequestNo(),
                        "employeeId", doc.employeeId().toString(),
                        "categoryName", doc.categoryName(),
                        "expiryDate", doc.expiryDate().toString()));

        log.debug("Created renewal request {} for document {} (employee: {}, category: {}, expires: {})",
                request.getRequestNo(), doc.documentId(), doc.employeeName(),
                doc.categoryName(), doc.expiryDate());

        return true;
    }

    private String generateRequestNo() {
        String sql = "SELECT nextval('selfservice.hr_service_request_no_seq')";
        Long seq = jdbc.queryForObject(sql, Map.of(), Long.class);
        return BusinessNumbers.format("HR", 6, seq == null ? 1 : seq);
    }
}
