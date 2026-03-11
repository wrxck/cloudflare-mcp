package com.cloudflare.mcp;

import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ToolAnnotationMapperTest {

    @Test
    void get_is_read_only() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.GET, "desc");
        assertTrue(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
        assertTrue(ann.idempotentHint());
    }

    @Test
    void head_is_read_only() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.HEAD, "desc");
        assertTrue(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
    }

    @Test
    void options_is_read_only() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.OPTIONS, "desc");
        assertTrue(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
    }

    @Test
    void post_is_not_read_only_not_idempotent() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.POST, "desc");
        assertFalse(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
        assertFalse(ann.idempotentHint());
    }

    @Test
    void put_is_idempotent() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.PUT, "desc");
        assertFalse(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
        assertTrue(ann.idempotentHint());
    }

    @Test
    void patch_is_not_idempotent() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.PATCH, "desc");
        assertFalse(ann.readOnlyHint());
        assertFalse(ann.destructiveHint());
        assertFalse(ann.idempotentHint());
    }

    @Test
    void delete_is_destructive_and_idempotent() {
        ToolAnnotations ann = ToolAnnotationMapper.map(PathItem.HttpMethod.DELETE, "desc");
        assertFalse(ann.readOnlyHint());
        assertTrue(ann.destructiveHint());
        assertTrue(ann.idempotentHint());
    }

    @ParameterizedTest
    @EnumSource(PathItem.HttpMethod.class)
    void all_methods_set_title(PathItem.HttpMethod method) {
        ToolAnnotations ann = ToolAnnotationMapper.map(method, "test description");
        assertEquals("test description", ann.title());
    }

    @ParameterizedTest
    @EnumSource(PathItem.HttpMethod.class)
    void all_methods_set_open_world(PathItem.HttpMethod method) {
        ToolAnnotations ann = ToolAnnotationMapper.map(method, "desc");
        assertTrue(ann.openWorldHint());
    }
}
