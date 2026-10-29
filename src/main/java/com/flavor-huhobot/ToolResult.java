package com.flavor.huhobot;

/** 工具执行结果：aiText 给模型，display 给 Discord。 */
public record ToolResult(String aiText, String displayTitle, String displayBody) {

    public static ToolResult text(String text) {
        return new ToolResult(text == null ? "" : text, "", "");
    }

    public static ToolResult card(String aiText, String title, String body) {
        return new ToolResult(aiText == null ? "" : aiText, title == null ? "" : title, body == null ? "" : body);
    }

    public boolean hasDisplay() {
        return displayTitle != null && !displayTitle.isBlank();
    }
}
