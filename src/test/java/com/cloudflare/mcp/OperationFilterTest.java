package com.cloudflare.mcp;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationFilterTest {

    private static ServerConfig configWithDefaults() {
        return new ServerConfig(
                false, "claude", null, null, null,
                List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(),
                240, 50000, 10, 30
        );
    }

    private static ServerConfig configWithIncludeTags(String... tags) {
        return new ServerConfig(
                false, "claude", null, null, null,
                List.of(tags), List.of(),
                List.of(), List.of(),
                List.of(), List.of(),
                240, 50000, 10, 30
        );
    }

    private static ServerConfig configWithExcludeTags(String... tags) {
        return new ServerConfig(
                false, "claude", null, null, null,
                List.of(), List.of(tags),
                List.of(), List.of(),
                List.of(), List.of(),
                240, 50000, 10, 30
        );
    }

    private static ServerConfig configWithIncludeMethods(String... methods) {
        return new ServerConfig(
                false, "claude", null, null, null,
                List.of(), List.of(),
                List.of(), List.of(),
                List.of(methods), List.of(),
                240, 50000, 10, 30
        );
    }

    private static ServerConfig configWithIncludePaths(String... paths) {
        return new ServerConfig(
                false, "claude", null, null, null,
                List.of(), List.of(),
                List.of(paths), List.of(),
                List.of(), List.of(),
                240, 50000, 10, 30
        );
    }

    private static Operation operationWithTags(String... tags) {
        Operation op = new Operation();
        op.setTags(List.of(tags));
        op.setOperationId("test-op");
        return op;
    }

    private static Operation operationNoTags() {
        Operation op = new Operation();
        op.setOperationId("test-op");
        return op;
    }

    @Nested
    class DefaultFilter {

        @Test
        void accepts_everything() {
            OperationFilter filter = new OperationFilter(configWithDefaults());
            assertTrue(filter.accepts("/zones", PathItem.HttpMethod.GET, operationWithTags("Zones")));
            assertTrue(filter.accepts("/dns", PathItem.HttpMethod.DELETE, operationNoTags()));
        }
    }

    @Nested
    class TagFiltering {

        @Test
        void include_tags_accepts_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludeTags("DNS Records", "Zones"));
            assertTrue(filter.accepts("/zones", PathItem.HttpMethod.GET, operationWithTags("Zones")));
        }

        @Test
        void include_tags_rejects_non_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludeTags("DNS Records"));
            assertFalse(filter.accepts("/zones", PathItem.HttpMethod.GET, operationWithTags("Workers")));
        }

        @Test
        void include_tags_rejects_untagged() {
            OperationFilter filter = new OperationFilter(configWithIncludeTags("DNS Records"));
            assertFalse(filter.accepts("/zones", PathItem.HttpMethod.GET, operationNoTags()));
        }

        @Test
        void include_tags_case_insensitive() {
            OperationFilter filter = new OperationFilter(configWithIncludeTags("dns records"));
            assertTrue(filter.accepts("/zones", PathItem.HttpMethod.GET, operationWithTags("DNS Records")));
        }

        @Test
        void exclude_tags_rejects_matching() {
            OperationFilter filter = new OperationFilter(configWithExcludeTags("Deprecated"));
            assertFalse(filter.accepts("/old", PathItem.HttpMethod.GET, operationWithTags("Deprecated")));
        }

        @Test
        void exclude_tags_accepts_non_matching() {
            OperationFilter filter = new OperationFilter(configWithExcludeTags("Deprecated"));
            assertTrue(filter.accepts("/zones", PathItem.HttpMethod.GET, operationWithTags("Zones")));
        }
    }

    @Nested
    class MethodFiltering {

        @Test
        void include_methods_accepts_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludeMethods("GET", "POST"));
            assertTrue(filter.accepts("/zones", PathItem.HttpMethod.GET, operationNoTags()));
        }

        @Test
        void include_methods_rejects_non_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludeMethods("GET"));
            assertFalse(filter.accepts("/zones", PathItem.HttpMethod.DELETE, operationNoTags()));
        }
    }

    @Nested
    class PathFiltering {

        @Test
        void include_paths_accepts_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludePaths("/zones/**"));
            assertTrue(filter.accepts("/zones/123/dns", PathItem.HttpMethod.GET, operationNoTags()));
        }

        @Test
        void include_paths_rejects_non_matching() {
            OperationFilter filter = new OperationFilter(configWithIncludePaths("/zones/**"));
            assertFalse(filter.accepts("/workers/scripts", PathItem.HttpMethod.GET, operationNoTags()));
        }
    }

    @Nested
    class GlobToPattern {

        @Test
        void double_star_matches_any_depth() {
            var pattern = OperationFilter.globToPattern("/zones/**");
            assertTrue(pattern.matcher("/zones/123/dns").matches());
            assertTrue(pattern.matcher("/zones/123").matches());
        }

        @Test
        void single_star_does_not_cross_slash() {
            var pattern = OperationFilter.globToPattern("/zones/*");
            assertTrue(pattern.matcher("/zones/123").matches());
            assertFalse(pattern.matcher("/zones/123/dns").matches());
        }

        @Test
        void question_mark_matches_single_char() {
            var pattern = OperationFilter.globToPattern("/zone?");
            assertTrue(pattern.matcher("/zones").matches());
            assertFalse(pattern.matcher("/zoness").matches());
        }

        @Test
        void literal_dot_escaped() {
            var pattern = OperationFilter.globToPattern("/api.v4");
            assertTrue(pattern.matcher("/api.v4").matches());
            assertFalse(pattern.matcher("/apixv4").matches());
        }
    }
}
