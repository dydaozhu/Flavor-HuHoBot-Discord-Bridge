package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工具注册表，负责生成 function calling 清单并分发执行。 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public boolean requiresApproval(String name) {
        Tool tool = tools.get(name);
        return tool != null && tool.requiresApproval();
    }

    public ArrayNode specs() {
        ArrayNode array = Json.arr();
        for (Tool tool : tools.values()) {
            ObjectNode spec = array.addObject();
            spec.put("type", "function");
            ObjectNode function = spec.putObject("function");
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.parameters());
        }
        return array;
    }

    public ToolResult execute(String name, ObjectNode arguments, ToolContext context) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.text("未知工具: " + name);
        }
        try {
            return tool.execute(context, arguments == null ? Json.obj() : arguments);
        } catch (Exception error) {
            return ToolResult.text("工具执行失败: " + (error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage()));
        }
    }
}
