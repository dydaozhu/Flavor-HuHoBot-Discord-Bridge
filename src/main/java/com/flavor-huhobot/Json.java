package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 统一的 JSON 工具。插件内部只使用 Jackson，避免再引入第二套 JSON 库。 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private Json() {
    }

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化 JSON", e);
        }
    }

    public static String pretty(JsonNode node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化 JSON", e);
        }
    }

    public static JsonNode read(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式错误", e);
        }
    }

    public static JsonNode readOrNull(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
