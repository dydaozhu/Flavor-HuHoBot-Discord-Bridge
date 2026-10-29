package com.flavor.huhobot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** 文本处理工具：颜色、分片、清理。 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    /**
     * 把文本转换为 Adventure 组件。
     *
     * <p>普通消息仍使用 &amp; 颜色代码；当文本包含 &lt;...&gt; 时先尝试 MiniMessage，
     * 解析失败则回退到旧版 &amp; 颜色代码，避免把帮助文本里的 &lt;游戏ID&gt; 当成标签。
     */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return LEGACY.deserialize(text);
        }
        try {
            return MINI.deserialize(legacyToMiniMessage(text));
        } catch (RuntimeException ignored) {
            return LEGACY.deserialize(text);
        }
    }

    /** 把 &amp; 颜色代码转换为 MiniMessage 标签，便于两种格式混用。 */
    private static String legacyToMiniMessage(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current != '&' || i + 1 >= input.length()) {
                out.append(current);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            if (code == '#' && i + 7 < input.length()) {
                String hex = input.substring(i + 2, i + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    out.append("<#").append(hex).append('>');
                    i += 7;
                    continue;
                }
            }
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> null;
            };
            if (tag == null) {
                out.append(current);
            } else {
                out.append(tag);
                i++;
            }
        }
        return out.toString();
    }

    /** 提取组件中的纯文本。 */
    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    /**
     * 清理用户输入：去掉 § 颜色符，把 &amp;、&lt;、&gt; 换成全角字符，
     * 避免玩家名/聊天内容注入颜色代码或 MiniMessage 标签。
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('§', ' ')
                .replace('&', '＆')
                .replace('<', '＜')
                .replace('>', '＞');
    }

    /** 去掉 Minecraft 颜色代码，用于 Discord 等不渲染 &/§ 颜色的场景。 */
    public static String stripColorCodes(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)[§&][0-9A-FK-OR]", "")
                .replaceAll("(?i)&#[0-9A-F]{6}", "");
    }

    /** 按行切分，单条不超过 max 个字符；超长单行会硬切。 */
    public static List<String> split(String content, int max) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return result;
        }
        int limit = Math.max(1, max);
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\R", -1)) {
            String rest = line;
            while (rest.length() > limit) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.add(rest.substring(0, limit));
                rest = rest.substring(limit);
            }
            int needed = rest.length() + (current.length() > 0 ? 1 : 0);
            if (current.length() > 0 && current.length() + needed > limit) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(rest);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** 粗略估算 token：4 个字符约 1 token，仅用于上下文裁剪。 */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4 + 1;
    }
}
