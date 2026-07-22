package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestBodiesTest {

    private static RequestBody bodyWith(String... contentTypes) {
        Content content = new Content();
        for (String ct : contentTypes) {
            content.addMediaType(ct, new MediaType());
        }
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(content);
        return requestBody;
    }

    @Test
    void selects_json_media_type_when_present() {
        RequestBody body = bodyWith("text/plain", "application/json");
        assertSame(body.getContent().get("application/json"),
                RequestBodies.selectMediaType(body));
        assertEquals("application/json", RequestBodies.selectContentType(body));
    }

    @Test
    void falls_back_to_first_media_type() {
        RequestBody body = bodyWith("text/plain", "application/xml");
        assertSame(body.getContent().get("text/plain"),
                RequestBodies.selectMediaType(body));
        assertEquals("text/plain", RequestBodies.selectContentType(body));
    }

    @Test
    void null_and_empty_bodies_handled() {
        assertNull(RequestBodies.selectMediaType(null));
        assertEquals("application/json", RequestBodies.selectContentType(null));

        RequestBody noContent = new RequestBody();
        assertNull(RequestBodies.selectMediaType(noContent));
        assertEquals("application/json", RequestBodies.selectContentType(noContent));

        RequestBody emptyContent = new RequestBody();
        emptyContent.setContent(new Content());
        assertNull(RequestBodies.selectMediaType(emptyContent));
        assertEquals("application/json", RequestBodies.selectContentType(emptyContent));
    }

    @Test
    void flattens_only_object_schemas_with_properties() {
        assertTrue(RequestBodies.flattensIntoProperties(
                Map.of("type", "object", "properties", Map.of())));
        assertFalse(RequestBodies.flattensIntoProperties(Map.of("type", "object")));
        assertFalse(RequestBodies.flattensIntoProperties(Map.of("type", "string")));
    }
}
