package com.flavor.huhobot;

/** 将 Minecraft MOTD 的 § 颜色/格式代码转换为 HTML。 */
public final class MotdHtml {

    private MotdHtml() {
    }

    public static String toHtml(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<span>");
        String color = "#FFFFFF";
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == 'x' && i + 13 < input.length()) {
                    String hex = readHex(input, i);
                    if (hex != null) {
                        color = hex;
                        html.append("</span><span style=\"").append(style(color, bold, italic, underline, strike)).append("\">");
                        i += 14;
                        continue;
                    }
                }
                String nextColor = colorFor(code);
                if (nextColor != null) {
                    color = nextColor;
                    bold = italic = underline = strike = false;
                    html.append("</span><span style=\"").append(style(color, bold, italic, underline, strike)).append("\">");
                    i += 2;
                    continue;
                }
                switch (code) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> underline = true;
                    case 'm' -> strike = true;
                    case 'r' -> {
                        color = "#FFFFFF";
                        bold = italic = underline = strike = false;
                    }
                    case 'k' -> {
                        // 图片无法实现动态乱码，忽略该格式。
                    }
                    default -> {
                        // 不是颜色代码，按普通字符处理。
                        appendChar(html, c);
                        i++;
                        continue;
                    }
                }
                html.append("</span><span style=\"").append(style(color, bold, italic, underline, strike)).append("\">");
                i += 2;
                continue;
            }
            if (c == '\n') {
                html.append("</span><br/><span style=\"")
                        .append(style(color, bold, italic, underline, strike)).append("\">");
                i++;
                continue;
            }
            appendChar(html, c);
            i++;
        }
        return html.append("</span>").toString();
    }

    static String style(String color, boolean bold, boolean italic,
                        boolean underline, boolean strike) {        StringBuilder style = new StringBuilder("color:").append(color).append(";");
        if (bold) {
            style.append("font-weight:bold;");
        }
        if (italic) {
            style.append("font-style:italic;");
        }
        if (underline || strike) {
            style.append("text-decoration:");
            if (underline) {
                style.append("underline ");
            }
            if (strike) {
                style.append("line-through");
            }
            style.append(";");
        }
        return style.toString();
    }

    private static String colorFor(char code) {
        return switch (code) {
            case '0' -> "#000000";
            case '1' -> "#0000AA";
            case '2' -> "#00AA00";
            case '3' -> "#00AAAA";
            case '4' -> "#AA0000";
            case '5' -> "#AA00AA";
            case '6' -> "#FFAA00";
            case '7' -> "#AAAAAA";
            case '8' -> "#555555";
            case '9' -> "#5555FF";
            case 'a' -> "#55FF55";
            case 'b' -> "#55FFFF";
            case 'c' -> "#FF5555";
            case 'd' -> "#FF55FF";
            case 'e' -> "#FFFF55";
            case 'f' -> "#FFFFFF";
            default -> null;
        };
    }

    private static String readHex(String input, int start) {
        StringBuilder hex = new StringBuilder("#");
        for (int offset = 2; offset <= 12; offset += 2) {
            if (start + offset + 1 >= input.length()
                    || (input.charAt(start + offset) != '§' && input.charAt(start + offset) != '&')) {
                return null;
            }
            char digit = input.charAt(start + offset + 1);
            if (!isHex(digit)) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private static void appendChar(StringBuilder html, char c) {
        char mapped = smallCaps(c);
        if (mapped != c) {
            html.append(mapped);
            return;
        }
        switch (c) {
            case '&' -> html.append("&amp;");
            case '<' -> html.append("&lt;");
            case '>' -> html.append("&gt;");
            case '"' -> html.append("&quot;");
            case '\'' -> html.append("&#39;");
            default -> html.append(c);
        }
    }

    /** 把 Minecraft 常见的 Unicode 小型大写字母映射为普通字母，避免字体缺字。 */
    static char smallCaps(char c) {
        return switch (c) {
            case 'ᴀ' -> 'A';
            case 'ʙ' -> 'B';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ғ' -> 'F';
            case 'ɢ' -> 'G';
            case 'ʜ' -> 'H';
            case 'ɪ' -> 'I';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ʟ' -> 'L';
            case 'ᴍ' -> 'M';
            case 'ɴ' -> 'N';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ǫ' -> 'Q';
            case 'ʀ' -> 'R';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ʏ' -> 'Y';
            case 'ᴢ' -> 'Z';
            default -> c;
        };
    }

    /** 将文本按 HTML 转义并处理小型大写字母。 */
    public static void appendText(StringBuilder html, String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                html.append("<br/>");
            } else {
                appendChar(html, c);
            }
        }
    }
}
