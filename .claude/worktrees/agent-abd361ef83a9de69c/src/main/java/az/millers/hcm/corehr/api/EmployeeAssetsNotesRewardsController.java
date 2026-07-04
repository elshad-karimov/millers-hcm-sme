package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.AssetRequest;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.api.dto.AssetReturnRequest;
import az.millers.hcm.corehr.api.dto.NoteRequest;
import az.millers.hcm.corehr.api.dto.NoteResponse;
import az.millers.hcm.corehr.api.dto.RewardRequest;
import az.millers.hcm.corehr.api.dto.RewardResponse;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.corehr.service.EmployeeNoteService;
import az.millers.hcm.corehr.service.EmployeeRewardService;

/**
 * REST surface for the three M72 sub-entities — assets, notes, rewards.
 * One controller, three URL prefixes under {@code /api/employees/{id}}.
 *
 * <p>Same role gate as M63 / M71 — HR_ADMIN / HR_SPECIALIST / DEPT_MANAGER
 * (scoped) / SYSTEM_ADMIN / AUDITOR read; HR_ADMIN / HR_SPECIALIST /
 * SYSTEM_ADMIN write. The note service additionally applies per-row
 * visibility filtering, so a DEPARTMENT_MANAGER may see fewer notes than
 * an HR_ADMIN for the same employee.
 */
@RestController
public class EmployeeAssetsNotesRewardsController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_HR_PLUS_MANAGERS;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_HR;

    private final EmployeeAssetService assets;
    private final EmployeeNoteService notes;
    private final EmployeeRewardService rewards;

    public EmployeeAssetsNotesRewardsController(EmployeeAssetService assets,
                                                 EmployeeNoteService notes,
                                                 EmployeeRewardService rewards) {
        this.assets = assets;
        this.notes = notes;
        this.rewards = rewards;
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/assets")
    @PreAuthorize(READ_ROLES)
    public List<AssetResponse> listAssets(
            @PathVariable UUID employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean assignedOnly) {
        return assets.listFor(employeeId, assignedOnly);
    }

    @PostMapping("/api/employees/{employeeId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse assignAsset(@PathVariable UUID employeeId,
                                      @RequestBody @Valid AssetRequest req) {
        return assets.assign(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/assets/{assetId}")
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse updateAsset(@PathVariable UUID employeeId,
                                      @PathVariable UUID assetId,
                                      @RequestBody @Valid AssetRequest req) {
        return assets.update(assetId, req);
    }

    @PostMapping("/api/employees/{employeeId}/assets/{assetId}/close")
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse closeAsset(@PathVariable UUID employeeId,
                                     @PathVariable UUID assetId,
                                     @RequestBody @Valid AssetReturnRequest req) {
        return assets.close(assetId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/assets/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteAsset(@PathVariable UUID employeeId, @PathVariable UUID assetId) {
        assets.delete(assetId);
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/notes")
    @PreAuthorize(READ_ROLES)
    public List<NoteResponse> listNotes(@PathVariable UUID employeeId) {
        return notes.listFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public NoteResponse createNote(@PathVariable UUID employeeId,
                                    @RequestBody @Valid NoteRequest req) {
        return notes.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/notes/{noteId}")
    @PreAuthorize(WRITE_ROLES)
    public NoteResponse updateNote(@PathVariable UUID employeeId,
                                    @PathVariable UUID noteId,
                                    @RequestBody @Valid NoteRequest req) {
        return notes.update(noteId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteNote(@PathVariable UUID employeeId, @PathVariable UUID noteId) {
        notes.delete(noteId);
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/rewards")
    @PreAuthorize(READ_ROLES)
    public List<RewardResponse> listRewards(@PathVariable UUID employeeId) {
        return rewards.listFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public RewardResponse createReward(@PathVariable UUID employeeId,
                                        @RequestBody @Valid RewardRequest req) {
        return rewards.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/rewards/{rewardId}")
    @PreAuthorize(WRITE_ROLES)
    public RewardResponse updateReward(@PathVariable UUID employeeId,
                                        @PathVariable UUID rewardId,
                                        @RequestBody @Valid RewardRequest req) {
        return rewards.update(rewardId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/rewards/{rewardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteReward(@PathVariable UUID employeeId, @PathVariable UUID rewardId) {
        rewards.delete(rewardId);
    }
}
