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

        @Test
        void blank_location_falls_back_to_bundled() {
            var openAPI = SpecLoader.load("   ");
            assertNotNull(openAPI);
            assertFalse(openAPI.getPaths().isEmpty());
        }

        @Test
        void loads_minimal_spec_from_file(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
                throws Exception {
            java.nio.file.Path spec = dir.resolve("mini.json");
            java.nio.file.Files.writeString(spec, """
                    {"openapi": "3.0.0",
                     "info": {"title": "Mini", "version": "1.0"},
                     "paths": {"/ping": {"get": {"operationId": "ping",
                       "responses": {"200": {"description": "ok"}}}}}}""");
            var openAPI = SpecLoader.load(spec.toString());
            assertEquals(1, openAPI.getPaths().size());
            assertNotNull(openAPI.getPaths().get("/ping").getGet());
        }

        @Test
        void spec_without_paths_throws(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
                throws Exception {
            java.nio.file.Path spec = dir.resolve("empty.json");
            java.nio.file.Files.writeString(spec, """
                    {"openapi": "3.0.0",
                     "info": {"title": "Empty", "version": "1.0"},
                     "paths": {}}""");
            var ex = assertThrows(SpecLoader.SpecLoadException.class,
                    () -> SpecLoader.load(spec.toString()));
            assertTrue(ex.getMessage().contains("no paths"));
        }
    }
}
