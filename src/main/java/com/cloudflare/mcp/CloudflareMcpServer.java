package com.cloudflare.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CloudflareMcpServer {

    private static final Logger log = LoggerFactory.getLogger(CloudflareMcpServer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SERVER_NAME = "cloudflare-mcp";
    private static final String SERVER_VERSION = resolveVersion();

    private static String resolveVersion() {
        String v = CloudflareMcpServer.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.fromArgs(args);

            if (config.install()) {
                Install.run(config.claudeBinary());
                return;
            }

            CloudflareAuth auth = config.resolveAuth();
            if (auth == null) {
                log.error("No Cloudflare credentials found");
                System.err.println("Error: No Cloudflare credentials configured.");
                System.err.println("Set one of:");
                System.err.println("  - CLOUDFLARE_API_KEY + CLOUDFLARE_EMAIL (Global API Key)");
                System.err.println("  - CLOUDFLARE_API_TOKEN (scoped API Token)");
                System.exit(1);
            }

            OpenAPI openAPI = SpecLoader.load();
            log.info("Loaded Cloudflare API spec ({} paths)", openAPI.getPaths().size());

            RateLimiter rateLimiter = new RateLimiter(config.maxRequestsPerMinute());
            RequestBuilder requestBuilder = new RequestBuilder(
                    auth, config.connectTimeoutSeconds(), config.requestTimeoutSeconds());
            HttpApiClient apiClient = new HttpApiClient(
                    requestBuilder, rateLimiter, config.connectTimeoutSeconds(), config.maxResponseLength());

            OperationFilter filter = new OperationFilter(config);
            ToolGenerator generator = new ToolGenerator(filter, apiClient);
            List<SyncToolSpecification> tools = generator.generate(openAPI);

            if (tools.isEmpty()) {
                log.error("No tools generated. Check --include-tags / --exclude-tags filters.");
                System.exit(1);
            }

            startServer(tools);
        } catch (Exception e) {
            log.error("Failed to start Cloudflare MCP server: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void startServer(List<SyncToolSpecification> tools) {
        var transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(OBJECT_MAPPER));

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Cloudflare MCP server");
            server.close();
        }));

        log.info("Cloudflare MCP server started ({} tools)", tools.size());
    }
}
