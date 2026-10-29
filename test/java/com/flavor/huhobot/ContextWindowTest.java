package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextWindowTest {

    @Test
    void keepsSystemMessageFirst() {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(message("system", "system prompt"));
        for (int i = 0; i < 100; i++) {
            messages.add(message("user", "message " + i));
        }
        List<ObjectNode> trimmed = ContextWindow.trim(messages, 800);
        assertEquals("system", trimmed.get(0).path("role").asText());
        assertTrue(trimmed.size() < messages.size());
    }

    @Test
    void neverSplitsToolGroup() {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(message("system", "system"));
        for (int i = 0; i < 20; i++) {
            messages.add(message("user", "task " + i));
            messages.add(assistantWithToolCall("call-" + i));
            messages.add(toolResult("call-" + i, "result " + i));
        }

        List<ObjectNode> trimmed = ContextWindow.trim(messages, 600);
        int assistantCalls = 0;
        int toolResults = 0;
        for (ObjectNode node : trimmed) {
            if ("assistant".equals(node.path("role").asText()) && node.has("tool_calls")) {
                assistantCalls++;
            }
            if ("tool".equals(node.path("role").asText())) {
                toolResults++;
            }
        }
        assertEquals(assistantCalls, toolResults);
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = Json.obj();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    private ObjectNode assistantWithToolCall(String id) {
        ObjectNode node = Json.obj();
        node.put("role", "assistant");
        node.putNull("content");
        ArrayNode calls = node.putArray("tool_calls");
        ObjectNode call = calls.addObject();
        call.put("id", id);
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", "mc_get_plugins");
        function.put("arguments", "{}");
        return node;
    }

    private ObjectNode toolResult(String id, String content) {
        ObjectNode node = Json.obj();
        node.put("role", "tool");
        node.put("tool_call_id", id);
        node.put("content", content);
        return node;
    }
}
