package az.millers.hcm.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Thin wrapper around ClickHouse's HTTP interface.
 * Uses FORMAT JSON for SELECT queries and FORMAT JSONEachRow for inserts.
 */
@Component
public class ClickHouseClient {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String database;
    private final String username;
    private final String password;

    public ClickHouseClient(
            RestTemplate rest,
            ObjectMapper mapper,
            @Value("${hcm.analytics.clickhouse.url:http://localhost:18123}") String baseUrl,
            @Value("${hcm.analytics.clickhouse.database:hcm_analytics}") String database,
            @Value("${hcm.analytics.clickhouse.username:default}") String username,
            @Value("${hcm.analytics.clickhouse.password:}") String password) {
        this.rest = rest;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    /** Build headers with ClickHouse credentials. */
    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-ClickHouse-User", username);
        h.set("X-ClickHouse-Key", password);
        return h;
    }

    /** Execute a DDL statement scoped to the configured database. */
    public void execute(String sql) {
        executeOn(sql, database);
    }

    /**
     * Execute a DDL statement against the given database.
     * Use "default" for CREATE DATABASE (the target DB may not exist yet).
     */
    public void executeOn(String sql, String db) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("query", sql)
                .queryParam("database", db)
                .build(false).toUriString();
        HttpHeaders h = authHeaders();
        rest.exchange(url, HttpMethod.POST, new HttpEntity<>(null, h), String.class);
    }

    /**
     * Execute a SELECT query and return rows as a list of string-keyed maps.
     * The SQL must NOT include FORMAT — this method appends FORMAT JSON.
     */
    public List<Map<String, Object>> query(String sql) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("database", database)
                    .build(false).toUriString();
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(sql + " FORMAT JSON", headers);
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, entity, String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank()) return Collections.emptyList();
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.path("data");
            List<Map<String, Object>> result = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode row : data) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    row.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
                    result.add(map);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("ClickHouse query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Insert a batch of rows as JSONEachRow into the given table.
     * rows: list of maps where keys are column names and values are primitives.
     */
    public void insertBatch(String table, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        try {
            StringBuilder ndjson = new StringBuilder();
            for (Map<String, Object> row : rows) {
                ndjson.append(mapper.writeValueAsString(row)).append('\n');
            }
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("query", "INSERT INTO " + table + " FORMAT JSONEachRow")
                    .queryParam("database", database)
                    .build(false).toUriString();
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(ndjson.toString(), headers);
            rest.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("ClickHouse insert into {} failed: {}", table, e.getMessage());
        }
    }

    /** Ping to test connectivity. Returns true if ClickHouse is reachable. */
    public boolean ping() {
        try {
            ResponseEntity<String> r = rest.getForEntity(baseUrl + "/ping", String.class);
            return r.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
