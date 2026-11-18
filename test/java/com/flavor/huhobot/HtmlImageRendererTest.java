package com.flavor.huhobot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlImageRendererTest {

    @Test
    void rendersMotdTemplateToPng() throws Exception {
        HtmlImageRenderer renderer = new HtmlImageRenderer(path ->
                HtmlImageRendererTest.class.getClassLoader().getResourceAsStream(path));
        String html = renderer.template("motd.html")
                .replace("{{ICON}}", renderer.defaultServerIcon())
                .replace("{{ADDRESS}}", "mc.hypixel.net")
                .replace("{{MOTD}}", MotdHtml.toHtml("§aHello §cWorld"))
                .replace("{{VERSION_LABEL}}", "版本")
                .replace("{{PLAYERS_LABEL}}", "在线人数")
                .replace("{{DELAY_LABEL}}", "延迟")
                .replace("{{VERSION}}", "1.21")
                .replace("{{PLAYERS}}", "2 / 100")
                .replace("{{DELAY}}", "33 ms")
                .replace("{{STATUS}}", "在线");

        byte[] png = renderer.render(html, 900, 320);
        java.nio.file.Files.write(java.nio.file.Path.of("target/motd-probe.png"), png);

        assertTrue(png.length > 1000);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 0x50 && png[2] == 0x4E && png[3] == 0x47);
    }

    @Test
    void rendersOnlineTemplateToPng() throws Exception {
        HtmlImageRenderer renderer = new HtmlImageRenderer(path ->
                HtmlImageRendererTest.class.getClassLoader().getResourceAsStream(path));
        StringBuilder cards = new StringBuilder("<table class=\"players\">");
        for (int i = 0; i < 8; i++) {
            if (i % 4 == 0) {
                cards.append("<tr>");
            }
            cards.append("<td class=\"player\"><table><tr><td><img class=\"head\" src=\"")
                    .append(renderer.steveHead())
                    .append("\" width=\"32\" height=\"32\" alt=\"\"/></td><td class=\"name\">Player")
                    .append(i + 1).append("</td></tr></table></td>");
            if (i % 4 == 3 || i == 7) {
                cards.append("</tr>");
            }
        }
        cards.append("</table>");
        String html = renderer.template("online.html")
                .replace("{{ICON}}", renderer.defaultServerIcon())
                .replace("{{SERVER_NAME}}", "Flavor-HuHoBot-Discord-Bridge")
                .replace("{{ONLINE_LABEL}}", "在线人数")
                .replace("{{COUNT}}", "8")
                .replace("{{MAX}}", "100")
                .replace("{{CHART}}", "")
                .replace("{{PLAYERS}}", cards.toString())
                .replace("{{EMPTY}}", "")
                .replace("{{FOOTER}}", "在线玩家列表");

        byte[] png = renderer.render(html, 900, 350);
        java.nio.file.Files.write(java.nio.file.Path.of("target/online-probe.png"), png);

        assertTrue(png.length > 1000);
    }
}
