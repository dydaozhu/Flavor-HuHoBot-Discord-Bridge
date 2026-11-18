package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;

/** 把 MineBBS 返回的 Minecraft JSON 聊天组件转换为 HTML。 */
public final class MotdComponent {

    private MotdComponent() {
    }

    public static String toHtml(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode root = Json.read(json);
            StringBuilder html = new StringBuilder();
            append(root, html, new Style());
            return html.toString();
        } catch (Exception ignored) {
            return MotdHtml.toHtml(json);
        }
    }

    private static void append(JsonNode node, StringBuilder html, Style parent) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            html.append("<span style=\"").append(parent.css()).append("\">");
            MotdHtml.appendText(html, node.asText());
            html.append("</span>");
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                append(child, html, parent);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        Style style = parent.inherit(node);
        html.append("<span style=\"").append(style.css()).append("\">");
        MotdHtml.appendText(html, node.path("text").asText(""));
        JsonNode extra = node.path("extra");
        if (extra.isArray()) {
            for (JsonNode child : extra) {
                append(child, html, style);
            }
        } else if (extra.isTextual()) {
            MotdHtml.appendText(html, extra.asText());
        }
        html.append("</span>");
    }

    private static final class Style {
        private String color = "#FFFFFF";
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean strike;

        private Style inherit(JsonNode node) {
            Style style = new Style();
            style.color = node.has("color") ? color(node.path("color").asText()) : color;
            style.bold = node.has("bold") ? node.path("bold").asBoolean() : bold;
            style.italic = node.has("italic") ? node.path("italic").asBoolean() : italic;
            style.underline = node.has("underlined") ? node.path("underlined").asBoolean() : underline;
            style.strike = node.has("strikethrough") ? node.path("strikethrough").asBoolean() : strike;
            return style;
        }

        private String css() {
            return MotdHtml.style(color, bold, italic, underline, strike);
        }

        private static String color(String name) {
            return switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "black" -> "#000000";
                case "dark_blue" -> "#0000AA";
                case "dark_green" -> "#00AA00";
                case "dark_aqua" -> "#00AAAA";
                case "dark_red" -> "#AA0000";
                case "dark_purple" -> "#AA00AA";
                case "gold" -> "#FFAA00";
                case "gray" -> "#AAAAAA";
                case "dark_gray" -> "#555555";
                case "blue" -> "#5555FF";
                case "green" -> "#55FF55";
                case "aqua" -> "#55FFFF";
                case "red" -> "#FF5555";
                case "light_purple" -> "#FF55FF";
                case "yellow" -> "#FFFF55";
                case "white" -> "#FFFFFF";
                default -> name.startsWith("#") && name.length() == 7 ? name : "#FFFFFF";
            };
        }
    }
}
