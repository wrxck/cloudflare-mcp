package com.cloudflare.mcp;

import org.junit.jupiter.api.Test;
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

    @Test
    void detectsApiError() {
        String json = """
                {"success": false, "errors": [{"code": 1003, "message": "Zone not found"}]}
                """;
        var ex = assertThrows(CloudflareRestClient.CloudflareApiException.class,
                () -> CloudflareRestClient.checkSuccess(json));
        assertTrue(ex.getMessage().contains("Zone not found"));
    }
}
