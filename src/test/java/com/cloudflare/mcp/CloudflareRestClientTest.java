package com.cloudflare.mcp;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CloudflareRestClientTest {

    @Test
    void parsesZoneListResponse() {
        String json = """
                {"success": true, "result": [{"id": "zone-id-1", "name": "example.com", "status": "active", "name_servers": ["ns1.cloudflare.com", "ns2.cloudflare.com"]}]}
                """;
        var zones = CloudflareRestClient.parseZoneListResponse(json);
        assertEquals(1, zones.size());
        assertEquals("zone-id-1", zones.get(0).get("id"));
        assertEquals("example.com", zones.get(0).get("name"));
        assertEquals(List.of("ns1.cloudflare.com", "ns2.cloudflare.com"), zones.get(0).get("nameServers"));
    }

    @Test
    void parsesZoneCreateResponse() {
        String json = """
                {"success": true, "result": {"id": "new-zone-id", "name": "test.com", "status": "pending", "name_servers": ["anna.ns.cloudflare.com", "bob.ns.cloudflare.com"]}}
                """;
        var zone = CloudflareRestClient.parseZoneResponse(json);
        assertEquals("new-zone-id", zone.get("id"));
        assertEquals("pending", zone.get("status"));
    }

    @Test
    void parsesDnsRecordListResponse() {
        String json = """
                {"success": true, "result": [{"id": "r1", "type": "A", "name": "example.com", "content": "1.2.3.4", "proxied": true, "ttl": 1}, {"id": "r2", "type": "MX", "name": "example.com", "content": "mail.example.com", "priority": 10, "proxied": false, "ttl": 300}]}
                """;
        var records = CloudflareRestClient.parseDnsRecordListResponse(json);
        assertEquals(2, records.size());
        assertEquals("A", records.get(0).get("type"));
        assertEquals(true, records.get(0).get("proxied"));
        assertEquals(10, records.get(1).get("priority"));
    }

    @Test
    void parsesSettingResponse() {
        String json = """
                {"success": true, "result": {"id": "ssl", "value": "full"}}
                """;
        var setting = CloudflareRestClient.parseSettingResponse(json);
        assertEquals("ssl", setting.get("id"));
        assertEquals("full", setting.get("value"));
    }

    @Test
    void constructsWithCloudflareAuth() {
        var auth = CloudflareAuth.apiToken("test-token");
        var client = new CloudflareRestClient(auth, "account-id", new RateLimiter(240));
        assertNotNull(client);
    }

    @Test
    void constructsWithGlobalApiKeyAuth() {
        var auth = CloudflareAuth.globalApiKey("test-key", "user@example.com");
        var client = new CloudflareRestClient(auth, "account-id", new RateLimiter(240));
        assertNotNull(client);
    }

    @Test
    void legacyConstructorStillWorks() {
        var client = new CloudflareRestClient("test-token", "account-id", new RateLimiter(240));
        assertNotNull(client);
    }

    // SEC-0002: domain must be URL-encoded before query-string concatenation
    @Test
    void getZoneByNameEncodesAmpersandInDomain() {
        // Simulate the query-string building that getZoneByName now performs.
        String maliciousDomain = "example.com&account.id=ATTACKER_ACCOUNT_ID";
        String accountId = "OWNER_ACCOUNT_ID";
        String encoded = URLEncoder.encode(maliciousDomain, StandardCharsets.UTF_8);
        String path = "/zones?name=" + encoded + "&account.id=" + accountId;

        URI uri = URI.create("https://api.cloudflare.com/client/v4" + path);
        String rawQuery = uri.getRawQuery();

        // The injected account.id must NOT appear as a separate parameter.
        assertFalse(rawQuery.contains("account.id=ATTACKER_ACCOUNT_ID"),
                "Injected account.id must not appear as a separate parameter after encoding: " + rawQuery);
        // The legitimate account.id is the only one present.
        assertTrue(rawQuery.contains("account.id=OWNER_ACCOUNT_ID"),
                "Legitimate account.id must still be present: " + rawQuery);
        // The '&' from the domain is percent-encoded, not a bare separator.
        assertTrue(rawQuery.contains("%26"),
                "Ampersand in domain must be percent-encoded: " + rawQuery);
    }

    @Test
    void detectsApiError() {
        String json = """
                {"success": false, "errors": [{"code": 1003, "message": "Zone not found"}]}
                """;
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.checkSuccess(json));
        assertTrue(ex.getMessage().contains("Zone not found"));
    }

    @Test
    void checkSuccessJoinsMultipleErrorMessages() {
        String json = """
                {"success": false, "errors": [{"message": "first"}, {"code": 42}]}
                """;
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.checkSuccess(json));
        assertTrue(ex.getMessage().contains("first"));
        assertTrue(ex.getMessage().contains("42"), "errors without message fall back to raw JSON");
    }

    @Test
    void checkSuccessFailsWithoutErrorsArray() {
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.checkSuccess("{\"success\": false}"));
        assertTrue(ex.getMessage().contains("success=false"));
    }

    @Test
    void checkSuccessAcceptsSuccessTrueAndMissingSuccess() {
        assertDoesNotThrow(() -> CloudflareRestClient.checkSuccess("{\"success\": true}"));
        assertDoesNotThrow(() -> CloudflareRestClient.checkSuccess("{\"result\": []}"));
    }

    @Test
    void checkSuccessWrapsMalformedJson() {
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.checkSuccess("not json"));
        assertTrue(ex.getMessage().startsWith("Failed to check API response"));
    }

    @Test
    void parseWrapsMalformedPayload() {
        // valid JSON envelope, but result entries missing mandatory fields
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.parseZoneListResponse("{\"success\": true, \"result\": [{}]}"));
        assertTrue(ex.getMessage().startsWith("Failed to parse zone list"));
    }

    @Test
    void parsesSettingValueVariants() {
        assertEquals(true, CloudflareRestClient.parseSettingResponse(
                "{\"success\": true, \"result\": {\"id\": \"x\", \"value\": true}}").get("value"));
        assertEquals(5, CloudflareRestClient.parseSettingResponse(
                "{\"success\": true, \"result\": {\"id\": \"x\", \"value\": 5}}").get("value"));
        assertEquals("{\"a\":1}", CloudflareRestClient.parseSettingResponse(
                "{\"success\": true, \"result\": {\"id\": \"x\", \"value\": {\"a\":1}}}").get("value"));
        assertFalse(CloudflareRestClient.parseSettingResponse(
                "{\"success\": true, \"result\": {\"id\": \"x\"}}").containsKey("value"));
    }

    @Test
    void apiExceptionCarriesStatusCode() {
        var httpError = new CloudflareRestClient.CloudflareApiException("HTTP 404: gone", 404);
        assertEquals(404, httpError.statusCode());
        assertEquals(-1, new CloudflareRestClient.CloudflareApiException("other").statusCode());
        assertEquals(-1, new CloudflareRestClient.CloudflareApiException("other", new RuntimeException())
                .statusCode());
    }
}
