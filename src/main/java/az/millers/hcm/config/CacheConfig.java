package az.millers.hcm.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * M168 (PRD §15.2): Redis-backed Spring Cache configuration.
 *
 * <p>Caches are applied to high-read, low-change reference data that is
 * expensive to re-query on every HTTP request:
 *
 * <ul>
 *   <li>{@code hcm:leave-types}    — LeaveTypeService — 30 min TTL</li>
 *   <li>{@code hcm:workflow-defs}  — WorkflowService  — 30 min TTL</li>
 *   <li>{@code hcm:org-units}      — OrgUnitService   — 10 min TTL</li>
 *   <li>{@code hcm:positions}      — PositionService  — 10 min TTL</li>
 *   <li>{@code hcm:grades}         — GradeService     — 60 min TTL</li>
 *   <li>{@code hcm:competencies}   — CompetencyService — 60 min TTL</li>
 * </ul>
 *
 * <p>Values are stored as JSON (GenericJackson2JsonRedisSerializer) so they
 * are human-readable in Redis CLI and survive JVM restarts between deploys.
 *
 * <p>Cache eviction is automatic on TTL expiry.  Services that modify
 * reference data must annotate their write methods with {@code @CacheEvict}
 * to flush the affected cache immediately without waiting for TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name constants — use these instead of magic strings. */
    public static final String LEAVE_TYPES    = "hcm:leave-types";
    public static final String WORKFLOW_DEFS  = "hcm:workflow-defs";
    public static final String ORG_UNITS      = "hcm:org-units";
    public static final String POSITIONS      = "hcm:positions";
    public static final String GRADES         = "hcm:grades";
    public static final String COMPETENCIES   = "hcm:competencies";

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Allow type info so polymorphic deserialization works across JVM restarts.
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = jsonSerializer();
        // Default: JSON serialisation, no null values, 5-minute TTL.
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(5));

        // Per-cache TTL overrides.
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(LEAVE_TYPES,   defaults.entryTtl(Duration.ofMinutes(30)));
        perCache.put(WORKFLOW_DEFS, defaults.entryTtl(Duration.ofMinutes(30)));
        perCache.put(ORG_UNITS,     defaults.entryTtl(Duration.ofMinutes(10)));
        perCache.put(POSITIONS,     defaults.entryTtl(Duration.ofMinutes(10)));
        perCache.put(GRADES,        defaults.entryTtl(Duration.ofMinutes(60)));
        perCache.put(COMPETENCIES,  defaults.entryTtl(Duration.ofMinutes(60)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
