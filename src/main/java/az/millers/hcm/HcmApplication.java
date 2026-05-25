package az.millers.hcm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Enterprise HCM platform.
 *
 * <p>Modular monolith: each business capability lives in its own package
 * ({@code corehr}, {@code audit}, {@code security}, ...) with microservice-ready
 * boundaries, as described in the PRD Section 12.
 *
 * <p>{@code @EnableScheduling} powers the {@code reporting} module's cron
 * walker that fires due {@code report_schedule} entries.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class HcmApplication {

    public static void main(String[] args) {
        SpringApplication.run(HcmApplication.class, args);
    }
}
