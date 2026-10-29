package com.flavor.huhobot;

import java.util.List;

/** 一次 Chat Completions 的结果。 */
public record ChatResult(String content, List<ToolCall> toolCalls, long tokens) {
}
