package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 上下文裁剪。
 *
 * <p>按“消息组”裁剪：assistant(tool_calls) 与其后的 tool 响应不可分割，
 * 否则 OpenAI 兼容接口会因为缺少 tool_call_id 响应直接报 400。
 */
public final class ContextWindow {

    private ContextWindow() {
    }

    public static List<ObjectNode> trim(List<ObjectNode> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        ObjectNode system = messages.get(0);
        List<List<ObjectNode>> groups = new ArrayList<>();
        List<ObjectNode> current = null;
        for (int i = 1; i < messages.size(); i++) {
            ObjectNode message = messages.get(i);
            String role = message.path("role").asText("");
            if ("assistant".equals(role) && message.has("tool_calls")) {
                current = new ArrayList<>();
                current.add(message);
                groups.add(current);
            } else if ("tool".equals(role) && current != null) {
                current.add(message);
            } else {
                current = null;
                List<ObjectNode> single = new ArrayList<>();
                single.add(message);
                groups.add(single);
            }
        }

        int budget = Math.max(500, maxTokens) - estimate(system);
        LinkedList<List<ObjectNode>> kept = new LinkedList<>();
        for (int i = groups.size() - 1; i >= 0; i--) {
            List<ObjectNode> group = groups.get(i);
            int cost = 0;
            for (ObjectNode message : group) {
                cost += estimate(message);
            }
            if (cost > budget) {
                break;
            }
            kept.addFirst(group);
            budget -= cost;
        }

        List<ObjectNode> result = new ArrayList<>();
        result.add(system);
        for (List<ObjectNode> group : kept) {
            result.addAll(group);
        }
        return result;
    }

    public static int estimate(ObjectNode message) {
        return Json.write(message).length() / 4 + 4;
    }
}
