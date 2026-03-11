package com.cloudflare.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Nested
    class FromArgs {

        @Test
        void defaults_when_no_args() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{});
            assertFalse(config.install());
            assertEquals("claude", config.claudeBinary());
            assertEquals(240, config.maxRequestsPerMinute());
            assertEquals(50_000, config.maxResponseLength());
            assertEquals(10, config.connectTimeoutSeconds());
            assertEquals(30, config.requestTimeoutSeconds());
            assertTrue(config.includeTags().isEmpty());
            assertTrue(config.excludeTags().isEmpty());
            assertTrue(config.includePaths().isEmpty());
            assertTrue(config.excludePaths().isEmpty());
            assertTrue(config.includeMethods().isEmpty());
            assertTrue(config.excludeMethods().isEmpty());
        }

        @Test
        void parses_install_flag() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--install"});
            assertTrue(config.install());
        }

        @Test
        void parses_claude_binary() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--claude-binary", "/usr/local/bin/claude"});
            assertEquals("/usr/local/bin/claude", config.claudeBinary());
        }

        @Test
        void parses_include_tags_csv() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--include-tags", "DNS Records,Zones,Workers"});
            assertEquals(3, config.includeTags().size());
            assertTrue(config.includeTags().contains("DNS Records"));
            assertTrue(config.includeTags().contains("Zones"));
            assertTrue(config.includeTags().contains("Workers"));
        }

        @Test
        void parses_exclude_tags_csv() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--exclude-tags", "Deprecated"});
            assertEquals(1, config.excludeTags().size());
            assertTrue(config.excludeTags().contains("Deprecated"));
        }

        @Test
        void parses_numeric_flags() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{
                    "--max-requests-per-minute", "120",
                    "--max-response-length", "25000",
                    "--connect-timeout", "5",
                    "--request-timeout", "15"
            });
            assertEquals(120, config.maxRequestsPerMinute());
            assertEquals(25_000, config.maxResponseLength());
            assertEquals(5, config.connectTimeoutSeconds());
            assertEquals(15, config.requestTimeoutSeconds());
        }

        @Test
        void parses_include_methods_csv() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--include-methods", "GET,POST"});
            assertEquals(2, config.includeMethods().size());
            assertTrue(config.includeMethods().contains("GET"));
            assertTrue(config.includeMethods().contains("POST"));
        }

        @Test
        void parses_include_paths_csv() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--include-paths", "/zones/**,/dns/**"});
            assertEquals(2, config.includePaths().size());
        }

        @Test
        void missing_value_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                    ServerConfig.fromArgs(new String[]{"--include-tags"}));
        }

        @Test
        void tags_list_is_immutable() {
            ServerConfig config = ServerConfig.fromArgs(new String[]{"--include-tags", "DNS Records"});
            assertThrows(UnsupportedOperationException.class, () ->
                    config.includeTags().add("extra"));
        }
    }

    @Nested
    class ResolveEnvVars {

        @Test
        void resolves_known_env_var() {
            String result = ServerConfig.resolveEnvVars("${PATH}");
            assertNotNull(result);
            assertFalse(result.contains("${"));
        }

        @Test
        void returns_null_for_missing_env_var() {
            String result = ServerConfig.resolveEnvVars("${THIS_VAR_DOES_NOT_EXIST_12345}");
            assertNull(result);
        }

        @Test
        void returns_null_for_null_input() {
            assertNull(ServerConfig.resolveEnvVars(null));
        }

        @Test
        void plain_string_unchanged() {
            assertEquals("hello", ServerConfig.resolveEnvVars("hello"));
        }
    }
}
