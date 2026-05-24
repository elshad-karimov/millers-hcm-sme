package az.millers.hcm.leave.domain;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.leave.api.dto.SeniorityBracket;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that transparently maps {@code List<SeniorityBracket>}
 * to/from the JSONB {@code seniority_brackets_json} column on
 * {@code leave_mgmt.leave_type} (added in V40 — M47).
 *
 * <p>A static {@link ObjectMapper} is safe here: converters are
 * Hibernate-instantiated singletons with no mutable state.
 */
@Converter
public class SeniorityBracketsConverter
        implements AttributeConverter<List<SeniorityBracket>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<List<SeniorityBracket>> TYPE_REF =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<SeniorityBracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(brackets);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public List<SeniorityBracket> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, TYPE_REF);
        } catch (Exception e) {
            return List.of();
        }
    }
}
