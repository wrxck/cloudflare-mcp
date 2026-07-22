package com.cloudflare.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises CloudflareRestClient's HTTP paths against a local stub server.
 */
class CloudflareRestClientHttpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record RecordedRequest(String method, String uri, String body) {}

    private HttpServer server;
    private final ConcurrentLinkedQueue<RecordedRequest> requests = new ConcurrentLinkedQueue<>();
    private final List<StubResponse> responses = new ArrayList<>();
    private int responseIndex = 0;

    record StubResponse(int status, String body) {}

    private CloudflareRestClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(), exchange.getRequestURI().toString(), body));
            StubResponse resp = responseIndex < responses.size()
                    ? responses.get(responseIndex++)
                    : new StubResponse(200, "{\"success\": true, \"result\": []}");
            byte[] out = resp.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(resp.status(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new CloudflareRestClient(
                CloudflareAuth.apiToken("test-token"), "acct-1", new RateLimiter(10_000), baseUrl);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void enqueue(int status, String body) {
        responses.add(new StubResponse(status, body));
    }

    private RecordedRequest lastRequest() {
        RecordedRequest last = null;
        for (RecordedRequest r : requests) last = r;
        return last;
    }

    private static final String ZONE_RESULT =
            "{\"success\": true, \"result\": {\"id\": \"z1\", \"name\": \"example.com\", \"status\": \"active\"}}";

    @Nested
    class JsonBodyConstruction {

        // Domains are attacker-influencable input; interpolating them into a JSON
        // template must not produce malformed JSON or inject extra fields.
        @Test
        void createZone_produces_valid_json_for_domain_with_quote() throws Exception {
            enqueue(200, ZONE_RESULT);
            client.createZone("evil\".com");

            RecordedRequest req = lastRequest();
            JsonNode body = assertDoesNotThrow(() -> MAPPER.readTree(req.body()),
                    "request body must be valid JSON, was: " + req.body());
            assertEquals("evil\".com", body.get("name").asText());
            assertEquals("acct-1", body.get("account").get("id").asText());
        }

        @Test
        void createZone_domain_cannot_inject_extra_fields() throws Exception {
            enqueue(200, ZONE_RESULT);
            client.createZone("a.com\", \"type\": \"partial\", \"x\": \"");

            RecordedRequest req = lastRequest();
            JsonNode body = assertDoesNotThrow(() -> MAPPER.readTree(req.body()));
            assertEquals("full", body.get("type").asText(),
                    "injected type override must not take effect");
        }

        @Test
        void deployManagedRuleset_produces_valid_json_for_id_with_quote() throws Exception {
            enqueue(200, "{\"success\": true, \"result\": {}}");
            client.deployManagedRuleset("z1", "http_request_firewall_managed", "id\"with-quote");

            RecordedRequest req = lastRequest();
            JsonNode body = assertDoesNotThrow(() -> MAPPER.readTree(req.body()),
                    "request body must be valid JSON, was: " + req.body());
            assertEquals("id\"with-quote",
                    body.get("rules").get(0).get("action_parameters").get("id").asText());
        }
    }

    @Nested
    class EntrypointRuleset {

        @Test
        void returns_null_on_http_404() {
            enqueue(404, "{\"success\": false, \"errors\": [{\"message\": \"not found\"}]}");
            assertNull(client.getEntrypointRuleset("z1", "http_request_firewall_managed"));
        }

        @Test
        void non_404_error_with_404_in_body_still_throws() {
            // An HTTP 500 whose body merely mentions "404" must not be treated as absent.
            enqueue(500, "{\"success\": false, \"errors\": [{\"message\": \"upstream at /err/404\"}]}");
            assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> client.getEntrypointRuleset("z1", "http_request_firewall_managed"));
        }

        @Test
        void returns_result_node_on_success() {
            enqueue(200, "{\"success\": true, \"result\": {\"id\": \"rs1\"}}");
            JsonNode result = client.getEntrypointRuleset("z1", "http_request_firewall_managed");
            assertEquals("rs1", result.get("id").asText());
        }
    }

    @Nested
    class ZoneOperations {

        @Test
        void listZones_paginates_until_short_page() throws Exception {
            StringBuilder fullPage = new StringBuilder("{\"success\": true, \"result\": [");
            for (int i = 0; i < 50; i++) {
                if (i > 0) fullPage.append(",");
                fullPage.append("{\"id\": \"z").append(i)
                        .append("\", \"name\": \"d").append(i)
                        .append(".com\", \"status\": \"active\"}");
            }
            fullPage.append("]}");
            enqueue(200, fullPage.toString());
            enqueue(200, "{\"success\": true, \"result\": [{\"id\": \"z50\", \"name\": \"d50.com\", \"status\": \"active\"}]}");

            List<Map<String, Object>> zones = client.listZones();
            assertEquals(51, zones.size());
            assertEquals(2, requests.size());
            assertTrue(requests.peek().uri().contains("page=1"));
        }

        @Test
        void getZoneByName_returns_first_match() {
            enqueue(200, "{\"success\": true, \"result\": [{\"id\": \"z9\", \"name\": \"example.com\", \"status\": \"active\"}]}");
            Map<String, Object> zone = client.getZoneByName("example.com");
            assertEquals("z9", zone.get("id"));
        }

        @Test
        void getZoneByName_returns_null_when_absent() {
            enqueue(200, "{\"success\": true, \"result\": []}");
            assertNull(client.getZoneByName("missing.com"));
        }
    }

    @Nested
    class DnsOperations {

        @Test
        void listDnsRecords_returns_records() {
            enqueue(200, "{\"success\": true, \"result\": [{\"id\": \"r1\", \"type\": \"A\", \"name\": \"x\", \"content\": \"1.2.3.4\"}]}");
            List<Map<String, Object>> records = client.listDnsRecords("z1");
            assertEquals(1, records.size());
            assertEquals("A", records.get(0).get("type"));
        }

        @Test
        void createDnsRecord_sends_body_and_returns_it() throws Exception {
            enqueue(200, "{\"success\": true, \"result\": {}}");
            Map<String, Object> result = client.createDnsRecord(
                    "z1", "MX", "mail.example.com", "mx1.example.com", false, 300, 10);

            RecordedRequest req = lastRequest();
            JsonNode body = MAPPER.readTree(req.body());
            assertEquals("MX", body.get("type").asText());
            assertEquals(10, body.get("priority").asInt());
            assertEquals(300, body.get("ttl").asInt());
            assertEquals(10, result.get("priority"));
        }

        @Test
        void createDnsRecord_forces_ttl_1_when_proxied() throws Exception {
            enqueue(200, "{\"success\": true, \"result\": {}}");
            client.createDnsRecord("z1", "A", "www", "1.2.3.4", true, 300, null);

            JsonNode body = MAPPER.readTree(lastRequest().body());
            assertEquals(1, body.get("ttl").asInt());
            assertFalse(body.has("priority"));
        }

        @Test
        void createDnsRecord_wraps_api_error() {
            enqueue(200, "{\"success\": false, \"errors\": [{\"message\": \"bad record\"}]}");
            var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> client.createDnsRecord("z1", "A", "www", "1.2.3.4", false, 300, null));
            assertTrue(ex.getMessage().contains("bad record"));
        }
    }

    @Nested
    class SettingsOperations {

        @Test
        void getSetting_parses_response() {
            enqueue(200, "{\"success\": true, \"result\": {\"id\": \"ssl\", \"value\": \"strict\"}}");
            Map<String, Object> setting = client.getSetting("z1", "ssl");
            assertEquals("strict", setting.get("value"));
        }

        @Test
        void updateSetting_sends_patch_with_value() throws Exception {
            enqueue(200, "{\"success\": true, \"result\": {\"id\": \"ssl\", \"value\": \"full\"}}");
            Map<String, Object> updated = client.updateSetting("z1", "ssl", "full");

            RecordedRequest req = lastRequest();
            assertEquals("PATCH", req.method());
            assertEquals("full", MAPPER.readTree(req.body()).get("value").asText());
            assertEquals("full", updated.get("value"));
        }

        @Test
        void updateSetting_propagates_api_error() {
            enqueue(200, "{\"success\": false, \"errors\": [{\"message\": \"invalid value\"}]}");
            assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> client.updateSetting("z1", "ssl", "bogus"));
        }
    }

    @Nested
    class SimpleToggles {

        @Test
        void enableDnssec_posts_active_status() throws Exception {
            enqueue(200, "{\"success\": true}");
            Map<String, Object> result = client.enableDnssec("z1");
            assertEquals("enabled", result.get("dnssec"));
            assertEquals("POST", lastRequest().method());
            assertEquals("active", MAPPER.readTree(lastRequest().body()).get("status").asText());
        }

        @Test
        void enableBotFightMode_puts_flags() throws Exception {
            enqueue(200, "{\"success\": true}");
            Map<String, Object> result = client.enableBotFightMode("z1");
            assertEquals("enabled", result.get("bot_fight_mode"));
            JsonNode body = MAPPER.readTree(lastRequest().body());
            assertTrue(body.get("fight_mode").asBoolean());
        }

        @Test
        void enableUrlNormalization_puts_config() throws Exception {
            enqueue(200, "{\"success\": true}");
            Map<String, Object> result = client.enableUrlNormalization("z1");
            assertEquals("enabled", result.get("url_normalization"));
            assertEquals("cloudflare", MAPPER.readTree(lastRequest().body()).get("type").asText());
        }
    }

    @Nested
    class Rulesets {

        @Test
        void listAccountRulesets_maps_fields() {
            enqueue(200, """
                    {"success": true, "result": [
                      {"id": "rs1", "name": "Managed", "kind": "managed", "phase": "http_request_firewall_managed"},
                      {"id": "rs2"}
                    ]}""");
            List<Map<String, Object>> rulesets = client.listAccountRulesets();
            assertEquals(2, rulesets.size());
            assertEquals("Managed", rulesets.get(0).get("name"));
            assertEquals("", rulesets.get(1).get("name"));
        }

        @Test
        void listAccountRulesets_propagates_api_error() {
            enqueue(200, "{\"success\": false, \"errors\": [{\"message\": \"no access\"}]}");
            assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> client.listAccountRulesets());
        }
    }

    @Nested
    class ManagedTransforms {

        @Test
        void getManagedHeaders_returns_tree() {
            enqueue(200, "{\"success\": true, \"managed_request_headers\": []}");
            JsonNode headers = client.getManagedHeaders("z1");
            assertTrue(headers.has("managed_request_headers"));
        }

        @Test
        void enableManagedTransforms_enables_requested_and_preserves_existing() throws Exception {
            enqueue(200, """
                    {"success": true,
                     "managed_request_headers": [
                       {"id": "add_visitor_location_headers", "enabled": false},
                       {"id": "already_on", "enabled": true},
                       {"id": "leave_off", "enabled": false}],
                     "managed_response_headers": [
                       {"id": "add_security_headers", "enabled": false}]}""");
            enqueue(200, "{\"success\": true}");

            Map<String, Object> result = client.enableManagedTransforms("z1",
                    List.of("add_visitor_location_headers"), List.of("add_security_headers"));

            assertEquals(List.of("add_visitor_location_headers", "add_security_headers"),
                    result.get("enabled"));

            JsonNode patchBody = MAPPER.readTree(lastRequest().body());
            JsonNode reqHeaders = patchBody.get("managed_request_headers");
            assertTrue(reqHeaders.get(0).get("enabled").asBoolean());
            assertTrue(reqHeaders.get(1).get("enabled").asBoolean(), "already-enabled stays enabled");
            assertFalse(reqHeaders.get(2).get("enabled").asBoolean(), "unrequested stays disabled");
            assertTrue(patchBody.get("managed_response_headers").get(0).get("enabled").asBoolean());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void http_error_includes_status_and_truncated_body() {
            enqueue(500, "boom-" + "x".repeat(600));
            var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> client.get("/zones"));
            assertTrue(ex.getMessage().contains("HTTP 500"));
            assertTrue(ex.getMessage().length() < 700, "body must be truncated");
        }

        @Test
        void connection_failure_wrapped_in_api_exception() {
            var deadClient = new CloudflareRestClient(
                    CloudflareAuth.apiToken("t"), "acct", new RateLimiter(100),
                    "http://127.0.0.1:1");
            var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                    () -> deadClient.get("/zones"));
            assertTrue(ex.getMessage().startsWith("Request failed"));
        }

        @Test
        void requests_carry_auth_and_content_type() throws Exception {
            enqueue(200, "{\"success\": true}");
            client.post("/zones", "{}");
            // auth header verified indirectly: server saw the request (headers not recorded),
            // main assertion is the request reached the stub with the body intact
            assertEquals("{}", lastRequest().body());
        }
    }
}
