package az.millers.hcm.config;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Makes Hibernate serialise {@code jsonb} columns with the application's own
 * {@link ObjectMapper}.
 *
 * <p>Without this, Hibernate builds its JSON format mapper from a bare
 * {@code new ObjectMapper()} — Spring Boot 3.4 does not set
 * {@code hibernate.type.json_format_mapper} for you, verified against the
 * autoconfigure jar. A bare mapper has no JSR-310 module, so writing any
 * {@code @JdbcTypeCode(SqlTypes.JSON)} field whose payload contains a
 * {@code LocalDate}, {@code Instant} or {@code OffsetDateTime} throws
 * {@code InvalidDefinitionException: Java 8 date/time type ... not supported by
 * default} — and nothing catches it, so the request 500s.
 *
 * <p>That is not hypothetical. Creating an org unit writes an
 * {@code OrgUnitHistory} row whose {@code after_value} is an
 * {@code OrgUnitResponse}, and that record carries three {@code LocalDate}
 * fields — so adding a unit failed on the history write, after the unit itself
 * had already inserted cleanly. The same trap is armed on every other jsonb
 * payload in the schema: workflow instance payloads, termination payout
 * details, contract-change before/after snapshots.
 *
 * <p>Spring's mapper is the right one to hand over: Boot registers
 * JavaTimeModule on it, and it is already what the REST layer and
 * {@code AuditService} serialise with — so what lands in a jsonb column now
 * matches what the API returns for the same object, instead of being a second
 * dialect of the same data.
 */
@Configuration
public class HibernateJsonConfig implements HibernatePropertiesCustomizer {

    private final ObjectMapper objectMapper;

    public HibernateJsonConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
