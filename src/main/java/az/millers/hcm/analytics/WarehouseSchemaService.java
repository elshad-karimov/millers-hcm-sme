package az.millers.hcm.analytics;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates ClickHouse warehouse tables on application startup.
 * Uses ReplacingMergeTree so repeated syncs are idempotent.
 */
@Service
public class WarehouseSchemaService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseSchemaService.class);

    private final ClickHouseClient ch;
    private final String database;

    public WarehouseSchemaService(ClickHouseClient ch,
                                  @Value("${hcm.analytics.clickhouse.database:hcm_analytics}") String database) {
        this.ch = ch;
        this.database = database;
    }

    @PostConstruct
    public void init() {
        if (!ch.ping()) {
            log.warn("ClickHouse not reachable — warehouse schema init skipped");
            return;
        }
        try {
            ch.executeOn("CREATE DATABASE IF NOT EXISTS " + database, "default");
            createTables();
            log.info("ClickHouse warehouse schema ready");
        } catch (Exception e) {
            log.warn("ClickHouse schema init failed (non-fatal): {}", e.getMessage());
        }
    }

    private void createTables() {
        ch.execute("""
            CREATE TABLE IF NOT EXISTS fact_employee (
                employee_id    String,
                snapshot_date  Date,
                first_name     String,
                last_name      String,
                department_name String,
                status         String,
                hire_date      String
            ) ENGINE = ReplacingMergeTree()
            ORDER BY (snapshot_date, employee_id)
            """);

        ch.execute("""
            CREATE TABLE IF NOT EXISTS fact_attendance (
                event_id       String,
                employee_id    String,
                event_date     Date,
                event_type     String,
                department_name String
            ) ENGINE = ReplacingMergeTree()
            ORDER BY (event_date, employee_id, event_id)
            """);

        ch.execute("""
            CREATE TABLE IF NOT EXISTS fact_payroll_result (
                result_id      String,
                run_id         String,
                employee_id    String,
                period_year    Int32,
                period_month   Int32,
                gross_amount   Decimal(18, 2),
                net_amount     Decimal(18, 2),
                income_tax     Decimal(18, 2),
                dsmf_employee  Decimal(18, 2)
            ) ENGINE = ReplacingMergeTree()
            ORDER BY (period_year, period_month, employee_id, result_id)
            """);

        ch.execute("""
            CREATE TABLE IF NOT EXISTS fact_leave_request (
                request_id     String,
                employee_id    String,
                leave_type_name String,
                start_date     Date,
                end_date       Date,
                total_days     Decimal(10, 2),
                status         String,
                department_name String
            ) ENGINE = ReplacingMergeTree()
            ORDER BY (start_date, employee_id, request_id)
            """);
    }
}
