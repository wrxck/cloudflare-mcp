package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaConverterTest {

    @Nested
    class PrimitiveTypes {

        @Test
        void string_schema() {
            Schema<?> schema = new StringSchema();
            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("string", result.get("type"));
        }

        @Test
        void integer_schema() {
            Schema<?> schema = new IntegerSchema();
            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("integer", result.get("type"));
        }

        @Test
        void number_schema() {
            Schema<?> schema = new NumberSchema();
            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("number", result.get("type"));
        }

        @Test
        void boolean_schema() {
            Schema<?> schema = new BooleanSchema();
            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("boolean", result.get("type"));
        }
    }

    @Nested
    class ObjectSchema_Tests {

        @Test
        void object_with_properties() {
            ObjectSchema schema = new ObjectSchema();
            schema.addProperty("name", new StringSchema());
            schema.addProperty("age", new IntegerSchema());

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("object", result.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            assertNotNull(props);
            assertEquals(2, props.size());
            assertTrue(props.containsKey("name"));
            assertTrue(props.containsKey("age"));
        }

        @Test
        void object_with_required() {
            ObjectSchema schema = new ObjectSchema();
            schema.addProperty("name", new StringSchema());
            schema.setRequired(List.of("name"));

            Map<String, Object> result = SchemaConverter.convert(schema);

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) result.get("required");
            assertNotNull(required);
            assertTrue(required.contains("name"));
        }
    }

    @Nested
    class ArraySchemaTests {

        @Test
        void array_of_strings() {
            ArraySchema schema = new ArraySchema();
            schema.setItems(new StringSchema());

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("array", result.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) result.get("items");
            assertNotNull(items);
            assertEquals("string", items.get("type"));
        }

        @Test
        void array_with_min_max_items() {
            ArraySchema schema = new ArraySchema();
            schema.setItems(new StringSchema());
            schema.setMinItems(1);
            schema.setMaxItems(10);

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals(1, result.get("minItems"));
            assertEquals(10, result.get("maxItems"));
        }
    }

    @Nested
    class EnumsAndFormats {

        @Test
        void enum_values() {
            StringSchema schema = new StringSchema();
            schema.setEnum(List.of("active", "inactive"));

            Map<String, Object> result = SchemaConverter.convert(schema);

            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) result.get("enum");
            assertNotNull(enumValues);
            assertEquals(2, enumValues.size());
        }

        @Test
        void format_preserved() {
            StringSchema schema = new StringSchema();
            schema.setFormat("date-time");

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("date-time", result.get("format"));
        }
    }

    @Nested
    class Constraints {

        @Test
        void string_length_constraints() {
            StringSchema schema = new StringSchema();
            schema.setMinLength(1);
            schema.setMaxLength(255);

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals(1, result.get("minLength"));
            assertEquals(255, result.get("maxLength"));
        }

        @Test
        void number_constraints() {
            NumberSchema schema = new NumberSchema();
            schema.setMinimum(new BigDecimal("0"));
            schema.setMaximum(new BigDecimal("100"));

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals(new BigDecimal("0"), result.get("minimum"));
            assertEquals(new BigDecimal("100"), result.get("maximum"));
        }

        @Test
        void pattern_constraint() {
            StringSchema schema = new StringSchema();
            schema.setPattern("^[a-z]+$");

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("^[a-z]+$", result.get("pattern"));
        }
    }

    @Nested
    class ComposedSchemas {

        @Test
        @SuppressWarnings("unchecked")
        void allOf_merges_properties() {
            ObjectSchema s1 = new ObjectSchema();
            s1.addProperty("name", new StringSchema());
            s1.setRequired(List.of("name"));

            ObjectSchema s2 = new ObjectSchema();
            s2.addProperty("age", new IntegerSchema());

            ComposedSchema composed = new ComposedSchema();
            composed.setAllOf(List.of(s1, s2));

            Map<String, Object> result = SchemaConverter.convert(composed);
            assertEquals("object", result.get("type"));
            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            assertTrue(props.containsKey("name"));
            assertTrue(props.containsKey("age"));
            List<String> required = (List<String>) result.get("required");
            assertTrue(required.contains("name"));
        }

        @Test
        void oneOf_preserved() {
            StringSchema s1 = new StringSchema();
            IntegerSchema s2 = new IntegerSchema();

            ComposedSchema composed = new ComposedSchema();
            composed.setOneOf(List.of(s1, s2));

            Map<String, Object> result = SchemaConverter.convert(composed);
            assertTrue(result.containsKey("oneOf"));
        }

        @Test
        void anyOf_preserved() {
            StringSchema s1 = new StringSchema();
            IntegerSchema s2 = new IntegerSchema();

            ComposedSchema composed = new ComposedSchema();
            composed.setAnyOf(List.of(s1, s2));

            Map<String, Object> result = SchemaConverter.convert(composed);
            assertTrue(result.containsKey("anyOf"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void null_schema_returns_object() {
            Map<String, Object> result = SchemaConverter.convert(null);
            assertEquals("object", result.get("type"));
        }

        @Test
        void description_preserved() {
            StringSchema schema = new StringSchema();
            schema.setDescription("Zone identifier");

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals("Zone identifier", result.get("description"));
        }

        @Test
        void default_value_preserved() {
            IntegerSchema schema = new IntegerSchema();
            schema.setDefault(20);

            Map<String, Object> result = SchemaConverter.convert(schema);
            assertEquals(20, result.get("default"));
        }
    }
}
