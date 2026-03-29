package com.cloudflare.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed REST client for common Cloudflare API v4 operations.
 * Use this when you need direct, typed access to Cloudflare APIs
 * rather than the OpenAPI-driven dynamic tool generation.
 */
public final class CloudflareRestClient {

    private static final Logger log = LoggerFactory.getLogger(CloudflareRestClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = "https://api.cloudflare.com/client/v4";

    private final CloudflareAuth auth;
    private final String accountId;
    private final RateLimiter rateLimiter;
    private final HttpClient httpClient;

    public CloudflareRestClient(CloudflareAuth auth, String accountId, RateLimiter rateLimiter) {
        this.auth = auth;
        this.accountId = accountId;
        this.rateLimiter = rateLimiter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Legacy constructor for backwards compatibility — uses API Token auth. */
    public CloudflareRestClient(String apiToken, String accountId, RateLimiter rateLimiter) {
        this(CloudflareAuth.apiToken(apiToken), accountId, rateLimiter);
    }

    // --- Zone operations ---

    public List<Map<String, Object>> listZones() {
        var allZones = new ArrayList<Map<String, Object>>();
        int page = 1;
        while (true) {
            String json = get("/zones?per_page=50&page=" + page + "&account.id=" + accountId);
            var zones = parseZoneListResponse(json);
            allZones.addAll(zones);
            if (zones.size() < 50) break;
            page++;
        }
        return allZones;
    }

    public Map<String, Object> getZoneByName(String domain) {
        String json = get("/zones?name=" + domain + "&account.id=" + accountId);
        var zones = parseZoneListResponse(json);
        return zones.isEmpty() ? null : zones.getFirst();
    }

    public Map<String, Object> createZone(String domain) {
        String body = """
                {"name": "%s", "account": {"id": "%s"}, "type": "full"}
                """.formatted(domain, accountId);
        String json = post("/zones", body);
        return parseZoneResponse(json);
    }

    // --- DNS operations ---

    public List<Map<String, Object>> listDnsRecords(String zoneId) {
        var allRecords = new ArrayList<Map<String, Object>>();
        int page = 1;
        while (true) {
            String json = get("/zones/" + zoneId + "/dns_records?per_page=100&page=" + page);
            var records = parseDnsRecordListResponse(json);
            allRecords.addAll(records);
            if (records.size() < 100) break;
            page++;
        }
        return allRecords;
    }

    public Map<String, Object> createDnsRecord(String zoneId, String type, String name,
                                                 String content, boolean proxied, int ttl,
                                                 Integer priority) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", type);
        body.put("name", name);
        body.put("content", content);
        body.put("proxied", proxied);
        body.put("ttl", proxied ? 1 : ttl);
        if (priority != null) body.put("priority", priority);

        try {
            String json = post("/zones/" + zoneId + "/dns_records", MAPPER.writeValueAsString(body));
            checkSuccess(json);
            return body;
        } catch (Exception e) {
            if (e instanceof CloudflareApiException) throw (CloudflareApiException) e;
            throw new CloudflareApiException("Failed to create DNS record: " + e.getMessage(), e);
        }
    }

    // --- Settings operations ---

    public Map<String, Object> getSetting(String zoneId, String settingId) {
        String json = get("/zones/" + zoneId + "/settings/" + settingId);
        return parseSettingResponse(json);
    }

    public Map<String, Object> updateSetting(String zoneId, String settingId, Object value) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("value", value));
            String json = patch("/zones/" + zoneId + "/settings/" + settingId, body);
            return parseSettingResponse(json);
        } catch (CloudflareApiException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudflareApiException("Failed to update setting " + settingId + ": " + e.getMessage(), e);
        }
    }

    // --- DNSSEC ---

    public Map<String, Object> enableDnssec(String zoneId) {
        String json = post("/zones/" + zoneId + "/dnssec", "{\"status\": \"active\"}");
        checkSuccess(json);
        return Map.of("dnssec", "enabled");
    }

    // --- Generic HTTP (public for advanced use) ---

    public String get(String path) { return request("GET", path, null); }
    public String post(String path, String body) { return request("POST", path, body); }
    public String patch(String path, String body) { return request("PATCH", path, body); }
    public String put(String path, String body) { return request("PUT", path, body); }

    private String request(String method, String path, String body) {
        rateLimiter.checkAndRecord();
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30));

            auth.applyHeaders(builder);

            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };

            log.debug("CF {} {}", method, path);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new CloudflareApiException("HTTP " + response.statusCode() + ": " +
                        truncate(response.body(), 500));
            }
            return response.body();
        } catch (CloudflareApiException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudflareApiException("Request failed: " + e.getMessage(), e);
        }
    }

    // --- JSON parsing (package-private for testing, public for library consumers) ---

    public static List<Map<String, Object>> parseZoneListResponse(String json) {
        checkSuccess(json);
        try {
            JsonNode root = MAPPER.readTree(json);
            var result = new ArrayList<Map<String, Object>>();
            for (JsonNode zone : root.get("result")) {
                var z = new LinkedHashMap<String, Object>();
                z.put("id", zone.get("id").asText());
                z.put("name", zone.get("name").asText());
                z.put("status", zone.get("status").asText());
                var ns = new ArrayList<String>();
                if (zone.has("name_servers")) {
                    for (JsonNode n : zone.get("name_servers")) ns.add(n.asText());
                }
                z.put("nameServers", ns);
                result.add(z);
            }
            return result;
        } catch (CloudflareApiException e) { throw e; }
        catch (Exception e) { throw new CloudflareApiException("Failed to parse zone list: " + e.getMessage(), e); }
    }

    public static Map<String, Object> parseZoneResponse(String json) {
        checkSuccess(json);
        try {
            JsonNode zone = MAPPER.readTree(json).get("result");
            var z = new LinkedHashMap<String, Object>();
            z.put("id", zone.get("id").asText());
            z.put("name", zone.get("name").asText());
            z.put("status", zone.get("status").asText());
            var ns = new ArrayList<String>();
            if (zone.has("name_servers")) {
                for (JsonNode n : zone.get("name_servers")) ns.add(n.asText());
            }
            z.put("nameServers", ns);
            return z;
        } catch (CloudflareApiException e) { throw e; }
        catch (Exception e) { throw new CloudflareApiException("Failed to parse zone: " + e.getMessage(), e); }
    }

    public static List<Map<String, Object>> parseDnsRecordListResponse(String json) {
        checkSuccess(json);
        try {
            JsonNode root = MAPPER.readTree(json);
            var result = new ArrayList<Map<String, Object>>();
            for (JsonNode rec : root.get("result")) {
                var r = new LinkedHashMap<String, Object>();
                r.put("id", rec.get("id").asText());
                r.put("type", rec.get("type").asText());
                r.put("name", rec.get("name").asText());
                r.put("content", rec.get("content").asText());
                r.put("proxied", rec.has("proxied") && rec.get("proxied").asBoolean());
                r.put("ttl", rec.has("ttl") ? rec.get("ttl").asInt() : 1);
                if (rec.has("priority")) r.put("priority", rec.get("priority").asInt());
                result.add(r);
            }
            return result;
        } catch (CloudflareApiException e) { throw e; }
        catch (Exception e) { throw new CloudflareApiException("Failed to parse DNS records: " + e.getMessage(), e); }
    }

    public static Map<String, Object> parseSettingResponse(String json) {
        checkSuccess(json);
        try {
            JsonNode result = MAPPER.readTree(json).get("result");
            var r = new LinkedHashMap<String, Object>();
            r.put("id", result.get("id").asText());
            if (result.has("value")) {
                JsonNode val = result.get("value");
                if (val.isTextual()) r.put("value", val.asText());
                else if (val.isBoolean()) r.put("value", val.asBoolean());
                else if (val.isNumber()) r.put("value", val.numberValue());
                else r.put("value", val.toString());
            }
            return r;
        } catch (CloudflareApiException e) { throw e; }
        catch (Exception e) { throw new CloudflareApiException("Failed to parse setting: " + e.getMessage(), e); }
    }

    public static void checkSuccess(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.has("success") && !root.get("success").asBoolean()) {
                JsonNode errors = root.get("errors");
                if (errors != null && errors.isArray() && !errors.isEmpty()) {
                    var msgs = new ArrayList<String>();
                    for (JsonNode err : errors) {
                        msgs.add(err.has("message") ? err.get("message").asText() : err.toString());
                    }
                    throw new CloudflareApiException("Cloudflare API error: " + String.join("; ", msgs));
                }
                throw new CloudflareApiException("Cloudflare API returned success=false");
            }
        } catch (CloudflareApiException e) { throw e; }
        catch (Exception e) { throw new CloudflareApiException("Failed to check API response: " + e.getMessage(), e); }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public static final class CloudflareApiException extends RuntimeException {
        public CloudflareApiException(String message) { super(message); }
        public CloudflareApiException(String message, Throwable cause) { super(message, cause); }
    }
}
