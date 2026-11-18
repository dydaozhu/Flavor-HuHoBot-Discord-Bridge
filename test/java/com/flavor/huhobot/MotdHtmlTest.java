package com.flavor.huhobot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdHtmlTest {

    @Test
    void convertsLegacyColorsAndFormatting() {
        String html = MotdHtml.toHtml("§aHello §lWorld");
        assertTrue(html.contains("color:#55FF55"));
        assertTrue(html.contains("font-weight:bold"));
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("World"));
    }

    @Test
    void escapesPlainHtml() {
        String html = MotdHtml.toHtml("§c<test> & value");
        assertTrue(html.contains("&lt;test&gt;"));
        assertTrue(html.contains("&amp;"));
    }

    @Test
    void convertsJsonComponentAndSmallCaps() {
        String json = """
                {"text":"","extra":[
                  {"text":"ᴅsᴍᴘ.ᴍᴄғᴡǫ.ᴛᴏᴘ","color":"gold"},
                  {"text":"[","color":"gray"},
                  {"text":"26.3","color":"white"},
                  {"text":"]","color":"gray"}
                ]}
                """;
        String html = MotdComponent.toHtml(json);
        assertTrue(html.contains("DsMP.MCFWQ.TOP"));
        assertTrue(html.contains("color:#FFAA00"));
        assertTrue(html.contains("color:#FFFFFF"));
    }
}
