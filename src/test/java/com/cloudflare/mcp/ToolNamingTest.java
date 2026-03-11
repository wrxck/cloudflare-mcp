package com.cloudflare.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ToolNamingTest {

    @Nested
    class Sanitize {

        @Test
        void keeps_alphanumeric_and_underscores() {
            assertEquals("list_zones", ToolNaming.sanitize("list_zones"));
        }

        @Test
        void keeps_hyphens() {
            assertEquals("list-zones", ToolNaming.sanitize("list-zones"));
        }

        @Test
        void replaces_special_chars_with_underscore() {
            assertEquals("list_zones", ToolNaming.sanitize("list.zones"));
        }

        @Test
        void collapses_multiple_underscores() {
            assertEquals("list_zones", ToolNaming.sanitize("list___zones"));
        }

        @Test
        void strips_leading_trailing_underscores() {
            assertEquals("list", ToolNaming.sanitize("_list_"));
        }

        @Test
        void empty_becomes_unnamed() {
            assertEquals("unnamed", ToolNaming.sanitize("..."));
        }

        @ParameterizedTest
        @CsvSource({
                "zones-0-get, zones-0-get",
                "zone/dns, zone_dns",
                "a b c, a_b_c"
        })
        void various_inputs(String input, String expected) {
            assertEquals(expected, ToolNaming.sanitize(input));
        }
    }

    @Nested
    class FromMethodAndPath {

        @Test
        void simple_path() {
            assertEquals("get_zones", ToolNaming.fromMethodAndPath("GET", "/zones"));
        }

        @Test
        void nested_path() {
            assertEquals("post_zones_dns_records",
                    ToolNaming.fromMethodAndPath("POST", "/zones/dns_records"));
        }

        @Test
        void path_with_params() {
            assertEquals("get_zones_zone_id_dns",
                    ToolNaming.fromMethodAndPath("GET", "/zones/{zone_id}/dns"));
        }

        @Test
        void path_with_hyphens() {
            assertEquals("delete_zones_zone_id",
                    ToolNaming.fromMethodAndPath("DELETE", "/zones/{zone-id}"));
        }

        @Test
        void strips_leading_trailing_slashes() {
            assertEquals("get_zones", ToolNaming.fromMethodAndPath("GET", "/zones/"));
        }
    }

    @Nested
    class Derive {

        @Test
        void prefers_operation_id() {
            var naming = new ToolNaming();
            assertEquals("listZones", naming.derive("listZones", "GET", "/zones"));
        }

        @Test
        void falls_back_to_method_path() {
            var naming = new ToolNaming();
            assertEquals("get_zones", naming.derive(null, "GET", "/zones"));
        }

        @Test
        void blank_operation_id_falls_back() {
            var naming = new ToolNaming();
            assertEquals("get_zones", naming.derive("  ", "GET", "/zones"));
        }

        @Test
        void ensures_uniqueness() {
            var naming = new ToolNaming();
            assertEquals("listZones", naming.derive("listZones", "GET", "/zones"));
            assertEquals("listZones_2", naming.derive("listZones", "GET", "/zones"));
            assertEquals("listZones_3", naming.derive("listZones", "GET", "/zones"));
        }

        @Test
        void truncates_long_names() {
            var naming = new ToolNaming();
            String longId = "a".repeat(100);
            String result = naming.derive(longId, "GET", "/");
            assertTrue(result.length() <= 64);
        }
    }
}
