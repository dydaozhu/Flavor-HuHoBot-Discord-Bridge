package com.flavor.huhobot;

/** 模型返回的一次函数调用。 */
public record ToolCall(String id, String name, String arguments) {
}
