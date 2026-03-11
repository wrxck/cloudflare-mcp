package com.cloudflare.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecLoaderTest {

    @Nested
    class LoadBundled {

        @Test
        void loads_bundled_spec() {
            var openAPI = SpecLoader.load();
            assertNotNull(openAPI);
            assertNotNull(openAPI.getPaths());
            assertFalse(openAPI.getPaths().isEmpty());
        }

        @Test
        void has_expected_title() {
            var openAPI = SpecLoader.load();
            assertNotNull(openAPI.getInfo());
            assertEquals("Cloudflare API", openAPI.getInfo().getTitle());
        }

        @Test
        void has_server_url() {
            var openAPI = SpecLoader.load();
            assertNotNull(openAPI.getServers());
            assertFalse(openAPI.getServers().isEmpty());
            assertTrue(openAPI.getServers().get(0).getUrl().contains("cloudflare.com"));
        }

        @Test
        void has_many_paths() {
            var openAPI = SpecLoader.load();
            assertTrue(openAPI.getPaths().size() > 1000,
                    "Expected >1000 paths, got " + openAPI.getPaths().size());
        }
    }

    @Nested
    class LoadFromLocation {

        @Test
        void invalid_location_throws() {
            assertThrows(SpecLoader.SpecLoadException.class, () ->
                    SpecLoader.load("/nonexistent/file.json"));
        }
    }
}
