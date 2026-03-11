package com.cloudflare.mcp;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolGeneratorTest {

    @Nested
    class BuildDescription {

        @Test
        void uses_summary_when_available() {
            Operation op = new Operation();
            op.setSummary("List zones");
            op.setDescription("Get all zones for the account");

            String desc = ToolGenerator.buildDescription(op, PathItem.HttpMethod.GET, "/zones");
            assertEquals("List zones", desc);
        }

        @Test
        void falls_back_to_description() {
            Operation op = new Operation();
            op.setDescription("Get all zones");

            String desc = ToolGenerator.buildDescription(op, PathItem.HttpMethod.GET, "/zones");
            assertEquals("Get all zones", desc);
        }

        @Test
        void truncates_long_description() {
            Operation op = new Operation();
            op.setDescription("x".repeat(300));

            String desc = ToolGenerator.buildDescription(op, PathItem.HttpMethod.GET, "/zones");
            assertTrue(desc.length() <= 200);
            assertTrue(desc.endsWith("..."));
        }

        @Test
        void falls_back_to_method_path() {
            Operation op = new Operation();

            String desc = ToolGenerator.buildDescription(op, PathItem.HttpMethod.GET, "/zones");
            assertEquals("GET /zones", desc);
        }

        @Test
        void blank_summary_falls_back() {
            Operation op = new Operation();
            op.setSummary("   ");
            op.setDescription("Get zones");

            String desc = ToolGenerator.buildDescription(op, PathItem.HttpMethod.GET, "/zones");
            assertEquals("Get zones", desc);
        }
    }

    @Nested
    class GetOperations {

        @Test
        void extracts_get() {
            PathItem item = new PathItem();
            item.setGet(new Operation().summary("list"));

            List<ToolGenerator.OperationEntry> ops = ToolGenerator.getOperations(item);
            assertEquals(1, ops.size());
            assertEquals(PathItem.HttpMethod.GET, ops.get(0).method());
        }

        @Test
        void extracts_multiple_methods() {
            PathItem item = new PathItem();
            item.setGet(new Operation().summary("list"));
            item.setPost(new Operation().summary("create"));
            item.setDelete(new Operation().summary("delete"));

            List<ToolGenerator.OperationEntry> ops = ToolGenerator.getOperations(item);
            assertEquals(3, ops.size());
        }

        @Test
        void empty_path_item() {
            PathItem item = new PathItem();
            List<ToolGenerator.OperationEntry> ops = ToolGenerator.getOperations(item);
            assertTrue(ops.isEmpty());
        }

        @Test
        void all_methods() {
            PathItem item = new PathItem();
            item.setGet(new Operation());
            item.setPost(new Operation());
            item.setPut(new Operation());
            item.setDelete(new Operation());
            item.setPatch(new Operation());
            item.setHead(new Operation());
            item.setOptions(new Operation());

            List<ToolGenerator.OperationEntry> ops = ToolGenerator.getOperations(item);
            assertEquals(7, ops.size());
        }
    }
}
