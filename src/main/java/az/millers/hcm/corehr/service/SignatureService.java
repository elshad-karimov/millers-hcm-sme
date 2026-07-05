package az.millers.hcm.corehr.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.corehr.domain.SignatureRequest;
import az.millers.hcm.corehr.domain.SignatureRequestSigner;
import az.millers.hcm.corehr.repo.SignatureRequestRepository;
import az.millers.hcm.corehr.repo.SignatureRequestSignerRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;

/**
 * M440 — Signature requests for employee documents and letters.
 * Internal signing only (click + timestamp + audit); DocuSign seam.
 */
@Service
@Transactional
public class SignatureService {

    private static final String TENANT = "default";
    private static final String MODULE = "core-hr";
    private static final String ENTITY = "SignatureRequest";

    private final SignatureRequestRepository requestRepo;
    private final SignatureRequestSignerRepository signerRepo;
    private final AuditService audit;
    private final NotificationService notifications;

    public SignatureService(SignatureRequestRepository requestRepo,
                           SignatureRequestSignerRepository signerRepo,
                           AuditService audit,
                           NotificationService notifications) {
        this.requestRepo = requestRepo;
        this.signerRepo = signerRepo;
        this.audit = audit;
        this.notifications = notifications;
    }

    public record SignatureRequestDTO(
            UUID id,
            String title,
            UUID employeeDocumentId,
            UUID letterRequestId,
            UUID attachmentId,
            String status,
            String provider,
            OffsetDateTime createdAt,
            String createdBy,
            List<SignerDTO> signers) {}

    public record SignerDTO(
            UUID id,
            UUID requestId,
            String username,
            UUID employeeId,
            String status,
            OffsetDateTime signedAt,
            String declineReason) {}

    public record CreateSignatureRequestInput(
            String title,
            UUID employeeDocumentId,
            UUID letterRequestId,
            UUID attachmentId,
            List<String> signerUsernames) {}

    /**
     * Create a new signature request (HR only).
     */
    public SignatureRequestDTO create(CreateSignatureRequestInput input) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        SignatureRequest request = new SignatureRequest();
        request.setTenantId(TENANT);
        request.setTitle(input.title());
        request.setEmployeeDocumentId(input.employeeDocumentId());
        request.setLetterRequestId(input.letterRequestId());
        request.setAttachmentId(input.attachmentId());
        request.setStatus(SignatureRequest.SignatureRequestStatus.PENDING);
        request.setProvider(SignatureRequest.SignatureProvider.INTERNAL);
        request.setCreatedBy(currentUser);
        request = requestRepo.save(request);

        List<SignatureRequestSigner> signers = new ArrayList<>();
        for (String username : input.signerUsernames()) {
            SignatureRequestSigner signer = new SignatureRequestSigner();
            signer.setTenantId(TENANT);
            signer.setRequestId(request.getId());
            signer.setUsername(username);
            signer.setStatus(SignatureRequestSigner.SignerStatus.PENDING);
            signers.add(signerRepo.save(signer));

            // Notify signer
            notifications.notifyAll(
                    NotificationCategory.WORKFLOW_APPROVAL,
                    username,
                    "Signature required: " + input.title(),
                    "You have been requested to sign: " + input.title(),
                    MODULE, ENTITY, request.getId().toString());
        }

        audit.record(MODULE, ENTITY, request.getId().toString(),
                "SIGNATURE_REQUEST_CREATED", null,
                Map.of("title", input.title(), "signerCount", signers.size()));

        return toDTO(request, signers);
    }

    /**
     * List all signature requests (HR).
     */
    public List<SignatureRequestDTO> listAll() {
        List<SignatureRequest> requests = requestRepo.findByTenantIdOrderByCreatedAtDesc(TENANT);
        return requests.stream()
                .map(r -> toDTO(r, signerRepo.findByRequestIdOrderByUsername(r.getId())))
                .toList();
    }

    /**
     * Get pending signature requests for current user (employee self-service).
     */
    public List<SignatureRequestDTO> listMy() {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        List<SignatureRequestSigner> signers = signerRepo.findPendingByUsername(TENANT, currentUser);

        return signers.stream()
                .map(signer -> {
                    SignatureRequest request = requestRepo.findById(signer.getRequestId())
                            .filter(r -> TENANT.equals(r.getTenantId()))
                            .orElse(null);
                    if (request == null) return null;
                    return toDTO(request, signerRepo.findByRequestIdOrderByUsername(request.getId()));
                })
                .filter(dto -> dto != null)
                .toList();
    }

    /**
     * Sign a document (employee can only sign their own assigned signatures).
     */
    public SignatureRequestDTO sign(UUID signerId) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        SignatureRequestSigner signer = signerRepo.findByIdAndTenantId(signerId, TENANT)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found"));

        // Verify caller IS the signer
        if (!currentUser.equals(signer.getUsername())) {
            throw new SecurityException("You can only sign your own requests");
        }

        if (signer.getStatus() != SignatureRequestSigner.SignerStatus.PENDING) {
            throw new IllegalStateException("Signer already acted on this request");
        }

        signer.setStatus(SignatureRequestSigner.SignerStatus.SIGNED);
        signer.setSignedAt(OffsetDateTime.now());
        signerRepo.save(signer);

        audit.record(MODULE, ENTITY, signer.getRequestId().toString(),
                "SIGNATURE_SIGNED", null,
                Map.of("signer", currentUser, "signedAt", signer.getSignedAt().toString()));

        // Check if all signers have signed
        SignatureRequest request = requestRepo.findByIdAndTenantId(signer.getRequestId(), TENANT)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        List<SignatureRequestSigner> allSigners = signerRepo.findByRequestIdOrderByUsername(request.getId());
        boolean allSigned = allSigners.stream()
                .allMatch(s -> s.getStatus() == SignatureRequestSigner.SignerStatus.SIGNED);

        if (allSigned) {
            request.setStatus(SignatureRequest.SignatureRequestStatus.COMPLETED);
            requestRepo.save(request);

            audit.record(MODULE, ENTITY, request.getId().toString(),
                    "SIGNATURE_REQUEST_COMPLETED", "PENDING",
                    Map.of("completedAt", OffsetDateTime.now().toString()));

            // Notify creator
            notifications.notifyAll(
                    NotificationCategory.WORKFLOW_OUTCOME,
                    request.getCreatedBy(),
                    "Signature completed: " + request.getTitle(),
                    "All signers have signed: " + request.getTitle(),
                    MODULE, ENTITY, request.getId().toString());
        }

        return toDTO(request, allSigners);
    }

    /**
     * Decline to sign.
     */
    public SignatureRequestDTO decline(UUID signerId, String reason) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        SignatureRequestSigner signer = signerRepo.findByIdAndTenantId(signerId, TENANT)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found"));

        // Verify caller IS the signer
        if (!currentUser.equals(signer.getUsername())) {
            throw new SecurityException("You can only decline your own requests");
        }

        if (signer.getStatus() != SignatureRequestSigner.SignerStatus.PENDING) {
            throw new IllegalStateException("Signer already acted on this request");
        }

        signer.setStatus(SignatureRequestSigner.SignerStatus.DECLINED);
        signer.setDeclineReason(reason);
        signerRepo.save(signer);

        SignatureRequest request = requestRepo.findByIdAndTenantId(signer.getRequestId(), TENANT)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        audit.record(MODULE, ENTITY, request.getId().toString(),
                "SIGNATURE_DECLINED", null,
                Map.of("signer", currentUser, "reason", reason == null ? "" : reason));

        // Notify creator
        notifications.notifyAll(
                NotificationCategory.WORKFLOW_OUTCOME,
                request.getCreatedBy(),
                "Signature declined: " + request.getTitle(),
                currentUser + " declined to sign: " + request.getTitle(),
                MODULE, ENTITY, request.getId().toString());

        List<SignatureRequestSigner> allSigners = signerRepo.findByRequestIdOrderByUsername(request.getId());
        return toDTO(request, allSigners);
    }

    /**
     * Cancel a signature request (creator or HR admin only).
     */
    public void cancel(UUID requestId) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        SignatureRequest request = requestRepo.findByIdAndTenantId(requestId, TENANT)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() == SignatureRequest.SignatureRequestStatus.CANCELLED) {
            throw new IllegalStateException("Request already cancelled");
        }

        if (request.getStatus() == SignatureRequest.SignatureRequestStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed request");
        }

        request.setStatus(SignatureRequest.SignatureRequestStatus.CANCELLED);
        requestRepo.save(request);

        audit.record(MODULE, ENTITY, request.getId().toString(),
                "SIGNATURE_REQUEST_CANCELLED", "PENDING",
                Map.of("cancelledBy", currentUser));

        // Notify pending signers
        List<SignatureRequestSigner> signers = signerRepo.findByRequestIdOrderByUsername(requestId);
        signers.stream()
                .filter(s -> s.getStatus() == SignatureRequestSigner.SignerStatus.PENDING)
                .forEach(s -> notifications.notifyAll(
                        NotificationCategory.WORKFLOW_OUTCOME,
                        s.getUsername(),
                        "Signature cancelled: " + request.getTitle(),
                        "The signature request has been cancelled: " + request.getTitle(),
                        MODULE, ENTITY, request.getId().toString()));
    }

    private SignatureRequestDTO toDTO(SignatureRequest request, List<SignatureRequestSigner> signers) {
        List<SignerDTO> signerDTOs = signers.stream()
                .map(s -> new SignerDTO(
                        s.getId(),
                        s.getRequestId(),
                        s.getUsername(),
                        s.getEmployeeId(),
                        s.getStatus().name(),
                        s.getSignedAt(),
                        s.getDeclineReason()))
                .toList();

        return new SignatureRequestDTO(
                request.getId(),
                request.getTitle(),
                request.getEmployeeDocumentId(),
                request.getLetterRequestId(),
                request.getAttachmentId(),
                request.getStatus().name(),
                request.getProvider().name(),
                request.getCreatedAt(),
                request.getCreatedBy(),
                signerDTOs);
    }
}
