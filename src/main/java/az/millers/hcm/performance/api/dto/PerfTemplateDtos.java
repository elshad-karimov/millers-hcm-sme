package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.performance.domain.PerfReviewTemplate;
import az.millers.hcm.performance.domain.PerfSectionType;
import az.millers.hcm.performance.domain.PerfTemplateSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** DTOs for review templates (HCM_12 M389). */
public final class PerfTemplateDtos {

    private PerfTemplateDtos() {}

    public record SectionRequest(
            @NotNull PerfSectionType sectionType,
            String title,
            @PositiveOrZero BigDecimal weightPercent,
            Boolean required) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 40) String templateCode,
            @NotBlank @Size(max = 160) String templateName,
            String description,
            UUID legalEntityId,
            UUID departmentId,
            UUID gradeId,
            String employeeType,
            Boolean active,
            @NotNull List<SectionRequest> sections) {
    }

    public record SectionResponse(
            UUID id,
            PerfSectionType sectionType,
            int sectionOrder,
            String title,
            BigDecimal weightPercent,
            boolean required,
            boolean scoring) {

        public static SectionResponse from(PerfTemplateSection s) {
            return new SectionResponse(s.getId(), s.getSectionType(), s.getSectionOrder(),
                    s.getTitle(), s.getWeightPercent(), s.isRequired(),
                    s.getSectionType().isScoring());
        }
    }

    public record TemplateResponse(
            UUID id,
            String templateCode,
            String templateName,
            String description,
            UUID legalEntityId,
            UUID departmentId,
            UUID gradeId,
            String employeeType,
            boolean active,
            List<SectionResponse> sections,
            BigDecimal scoringWeightTotal,
            OffsetDateTime createdAt) {

        public static TemplateResponse from(PerfReviewTemplate t, List<PerfTemplateSection> sections) {
            BigDecimal total = sections.stream()
                    .filter(s -> s.getSectionType().isScoring())
                    .map(PerfTemplateSection::getWeightPercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TemplateResponse(t.getId(), t.getTemplateCode(), t.getTemplateName(),
                    t.getDescription(), t.getLegalEntityId(), t.getDepartmentId(), t.getGradeId(),
                    t.getEmployeeType(), t.isActive(),
                    sections.stream().map(SectionResponse::from).toList(), total, t.getCreatedAt());
        }
    }
}
