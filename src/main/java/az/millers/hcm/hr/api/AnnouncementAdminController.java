package az.millers.hcm.hr.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.selfservice.api.dto.AnnouncementDto;
import az.millers.hcm.selfservice.api.dto.CreateAnnouncementDto;
import az.millers.hcm.selfservice.domain.Announcement;
import az.millers.hcm.selfservice.service.AnnouncementService;

/**
 * M430 — HR admin: announcement CRUD.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementAdminController {

    private final AnnouncementService service;

    public AnnouncementAdminController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<AnnouncementDto> list() {
        return service.listAll().stream()
                .map(AnnouncementDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public AnnouncementDto get(@PathVariable UUID id) {
        return AnnouncementDto.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public AnnouncementDto create(@RequestBody CreateAnnouncementDto dto) {
        Announcement ann = service.create(
                dto.title(),
                dto.body(),
                dto.publishFrom(),
                dto.publishTo(),
                dto.audience(),
                dto.audienceRef()
        );
        return AnnouncementDto.from(ann);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public AnnouncementDto update(@PathVariable UUID id, @RequestBody AnnouncementDto dto) {
        Announcement ann = service.update(
                id,
                dto.title(),
                dto.body(),
                dto.publishFrom(),
                dto.publishTo(),
                dto.audience(),
                dto.audienceRef(),
                dto.active()
        );
        return AnnouncementDto.from(ann);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
