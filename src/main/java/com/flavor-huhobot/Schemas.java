package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 构造 JSON Schema 的小工具。 */
public final class Schemas {

    private Schemas() {
    }

    public static ObjectNode object() {
        ObjectNode schema = Json.obj();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    public static ObjectNode property(ObjectNode schema, String name, String type, String description) {
        ObjectNode property = schema.with("properties").putObject(name);
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    public static void required(ObjectNode schema, String... names) {
        ArrayNode required = schema.putArray("required");
        for (String name : names) {
            required.add(name);
        }
    }
}
