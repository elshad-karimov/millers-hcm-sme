package az.millers.hcm.compensation.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.service.CompensationLetterService;
import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M371 — Compensation letter generation endpoints.
 * Reuses LetterRequestService for rendering and download.
 */
@RestController
@RequestMapping("/api/compensation")
public class CompensationLetterController {

    private final CompensationLetterService letterService;

    public CompensationLetterController(CompensationLetterService letterService) {
        this.letterService = letterService;
    }

    /**
     * Generate a salary increase letter for an APPLIED salary change request.
     * Returns the letter request info including the download path.
     */
    @PostMapping("/salary-changes/{id}/letter")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public ResponseEntity<Map<String, Object>> generateSalaryIncreaseLetter(@PathVariable UUID id) {
        LetterRequest letter = letterService.generateSalaryIncreaseLetter(id);
        return ResponseEntity.ok(Map.of(
                "letterRequestId", letter.getId(),
                "letterRequestNo", letter.getRequestNo(),
                "downloadPath", "/api/letter-requests/" + letter.getId() + "/pdf",
                "status", letter.getStatus().name()
        ));
    }
}
