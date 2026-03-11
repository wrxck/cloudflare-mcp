package com.cloudflare.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ToolGenerator {

    private static final Logger log = LoggerFactory.getLogger(ToolGenerator.class);

    private final OperationFilter filter;
    private final HttpApiClient apiClient;

    ToolGenerator(OperationFilter filter, HttpApiClient apiClient) {
        this.filter = filter;
        this.apiClient = apiClient;
    }

    List<SyncToolSpecification> generate(OpenAPI openAPI) {
        var tools = new ArrayList<SyncToolSpecification>();
        var naming = new ToolNaming();

        openAPI.getPaths().forEach((path, pathItem) -> {
            for (var entry : getOperations(pathItem)) {
                PathItem.HttpMethod method = entry.method;
                Operation operation = entry.operation;

                if (!filter.accepts(path, method, operation)) {
                    continue;
                }

                String toolName = naming.derive(operation.getOperationId(), method.name(), path);
                String description = buildDescription(operation, method, path);

                Map<String, Object> inputSchema = InputSchemaBuilder.build(
                        operation.getParameters(), operation.getRequestBody());

                McpSchema.ToolAnnotations annotations = ToolAnnotationMapper.map(method, description);

                @SuppressWarnings("unchecked")
                McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema(
                        "object",
                        (Map<String, Object>) inputSchema.get("properties"),
                        inputSchema.containsKey("required")
                                ? ((List<?>) inputSchema.get("required")).stream()
                                    .map(Object::toString).toList()
                                : null,
                        false, null, null);

                McpSchema.Tool tool = McpSchema.Tool.builder()
                        .name(toolName)
                        .description(description)
                        .inputSchema(jsonSchema)
                        .annotations(annotations)
                        .build();

                String capturedPath = path;
                String capturedMethod = method.name();

                SyncToolSpecification spec = SyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler((exchange, request) -> {
                            try {
                                var args = request.arguments() != null
                                        ? request.arguments() : Map.<String, Object>of();
                                String response = apiClient.execute(
                                        capturedPath, capturedMethod, operation, args);
                                return ResultHelper.sanitizedResult(response, apiClient.maxResponseLength());
                            } catch (Exception e) {
                                return ResultHelper.errorResult(e.getMessage());
                            }
                        })
                        .build();

                tools.add(spec);
            }
        });

        log.info("Generated {} tools from Cloudflare API spec", tools.size());
        return tools;
    }

    static String buildDescription(Operation operation, PathItem.HttpMethod method, String path) {
        if (operation.getSummary() != null && !operation.getSummary().isBlank()) {
            return operation.getSummary();
        }
        if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
            String desc = operation.getDescription();
            if (desc.length() > 200) {
                desc = desc.substring(0, 197) + "...";
            }
            return desc;
        }
        return method.name() + " " + path;
    }

    record OperationEntry(PathItem.HttpMethod method, Operation operation) {}

    static List<OperationEntry> getOperations(PathItem pathItem) {
        var ops = new ArrayList<OperationEntry>();
        if (pathItem.getGet() != null) ops.add(new OperationEntry(PathItem.HttpMethod.GET, pathItem.getGet()));
        if (pathItem.getPost() != null) ops.add(new OperationEntry(PathItem.HttpMethod.POST, pathItem.getPost()));
        if (pathItem.getPut() != null) ops.add(new OperationEntry(PathItem.HttpMethod.PUT, pathItem.getPut()));
        if (pathItem.getDelete() != null) ops.add(new OperationEntry(PathItem.HttpMethod.DELETE, pathItem.getDelete()));
        if (pathItem.getPatch() != null) ops.add(new OperationEntry(PathItem.HttpMethod.PATCH, pathItem.getPatch()));
        if (pathItem.getHead() != null) ops.add(new OperationEntry(PathItem.HttpMethod.HEAD, pathItem.getHead()));
        if (pathItem.getOptions() != null) ops.add(new OperationEntry(PathItem.HttpMethod.OPTIONS, pathItem.getOptions()));
        return ops;
    }
}
