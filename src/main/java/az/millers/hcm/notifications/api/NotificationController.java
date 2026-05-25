package az.millers.hcm.notifications.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.repo.NotificationLogRepository;

/**
 * REST API for the in-app notification inbox (PRD §17.5, Must-Have #34).
 *
 * <pre>
 * GET    /api/notifications               — paginated list (newest first)
 * GET    /api/notifications/unread-count  — unread badge count
 * PATCH  /api/notifications/{id}/read     — mark single notification read
 * POST   /api/notifications/read-all      — mark all read
 * </pre>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationLogRepository logRepo;
    private final NotificationService notificationService;

    public NotificationController(NotificationLogRepository logRepo,
                                   NotificationService notificationService) {
        this.logRepo = logRepo;
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return logRepo.findByRecipientOrderByCreatedAtDesc(username, PageRequest.of(page, size))
                .map(NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        long count = logRepo.countByRecipientAndReadAtIsNull(username);
        return Map.of("count", count);
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        notificationService.markRead(id, username);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        notificationService.markAllRead(username);
    }
}
