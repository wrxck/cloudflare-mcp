package com.cloudflare.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudflareMcpServerTest {

    @Test
    void createTools_generates_tools_from_bundled_spec() {
        ServerConfig config = ServerConfig.fromArgs(
                new String[]{"--include-tags", "DNS Records for a Zone"});
        List<SyncToolSpecification> tools = CloudflareMcpServer.createTools(
                config, CloudflareAuth.apiToken("test-token"));

        assertFalse(tools.isEmpty(), "DNS Records tag should produce tools from the bundled spec");
        for (SyncToolSpecification spec : tools) {
            assertNotNull(spec.tool().name());
            assertNotNull(spec.callHandler());
        }
    }

    @Test
    void createTools_returns_empty_for_unmatchable_filter() {
        ServerConfig config = ServerConfig.fromArgs(
                new String[]{"--include-tags", "No Such Tag Anywhere"});
        List<SyncToolSpecification> tools = CloudflareMcpServer.createTools(
                config, CloudflareAuth.apiToken("test-token"));
        assertTrue(tools.isEmpty());
    }
}
