package com.cloudflare.mcp;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SpecLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecLoader.class);
    private static final String BUNDLED_SPEC = "/cloudflare-openapi.json";

    private SpecLoader() {}

    static OpenAPI load() {
        return load(null);
    }

    static OpenAPI load(String location) {
        if (location != null && !location.isBlank()) {
            return loadFromLocation(location);
        }
        return loadBundled();
    }

    private static OpenAPI loadBundled() {
        log.info("Loading bundled Cloudflare OpenAPI spec");
        try {
            String specContent;
            try (InputStream is = SpecLoader.class.getResourceAsStream(BUNDLED_SPEC)) {
                if (is == null) {
                    throw new SpecLoadException("Bundled spec not found on classpath: " + BUNDLED_SPEC);
                }
                specContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            Path tempFile = Files.createTempFile("cloudflare-openapi-", ".json");
            Files.writeString(tempFile, specContent);
            tempFile.toFile().deleteOnExit();

            return parseSpec(tempFile.toString());
        } catch (SpecLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SpecLoadException("Failed to load bundled spec: " + e.getMessage(), e);
        }
    }

    private static OpenAPI loadFromLocation(String location) {
        log.info("Loading OpenAPI spec from: {}", location);
        return parseSpec(location);
    }

    private static OpenAPI parseSpec(String location) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        options.setFlatten(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(location, null, options);

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            int warningCount = result.getMessages().size();
            log.info("OpenAPI parser produced {} warnings (expected for large specs)", warningCount);
        }

        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            String errors = result.getMessages() != null
                    ? String.join("; ", result.getMessages().subList(0,
                    Math.min(5, result.getMessages().size())))
                    : "unknown error";
            throw new SpecLoadException("Failed to parse OpenAPI spec: " + errors);
        }

        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new SpecLoadException("OpenAPI spec contains no paths");
        }

        return openAPI;
    }

    static final class SpecLoadException extends RuntimeException {
        SpecLoadException(String message) {
            super(message);
        }

        SpecLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
