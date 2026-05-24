package az.millers.hcm.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint for AES-256-GCM key rotation (M48 — PRD 14.3).
 *
 * <pre>
 *   POST /api/admin/key-rotation/run             — rotate (live)
 *   POST /api/admin/key-rotation/run?dryRun=true — count only, no writes
 * </pre>
 *
 * <p>Requires {@code SYSTEM_ADMIN} role. The v2 key must be set in
 * {@code hcm.security.encryption.data-key-v2} (or the
 * {@code HCM_SECURITY_DATA_KEY_V2} env var) before calling this endpoint;
 * otherwise a 409 is returned.
 */
@RestController
@RequestMapping("/api/admin/key-rotation")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class KeyRotationController {

    private final KeyRotationService keyRotationService;

    public KeyRotationController(KeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    /**
     * Run (or dry-run) the key rotation.
     *
     * @param dryRun when {@code true} (default {@code false}), counts candidates
     *               without issuing any UPDATE statements.
     */
    @PostMapping("/run")
    public ResponseEntity<KeyRotationResult> run(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        try {
            KeyRotationResult result = keyRotationService.rotate(dryRun);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException ex) {
            // v2 key not configured
            return ResponseEntity.status(409).build();
        }
    }
}
