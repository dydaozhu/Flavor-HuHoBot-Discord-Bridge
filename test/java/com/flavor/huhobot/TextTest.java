package com.flavor.huhobot;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void splitsLongContentWithinLimit() {
        String content = "a".repeat(5000);
        List<String> parts = Text.split(content, 1900);
        assertTrue(parts.size() >= 3);
        for (String part : parts) {
            assertTrue(part.length() <= 1900, "part too long: " + part.length());
        }
        assertEquals(5000, parts.stream().mapToInt(String::length).sum());
    }

    @Test
    void splitsByLines() {
        String content = "line1\nline2\nline3";
        List<String> parts = Text.split(content, 100);
        assertEquals(1, parts.size());
        assertEquals(content, parts.get(0));
    }

    @Test
    void sanitizesColorCodes() {
        assertEquals(" c＆hello", Text.sanitize("§c&hello"));
    }

    @Test
    void truncatesWithEllipsis() {
        assertEquals(5, Text.truncate("abcdefgh", 5).length());
    }

    @Test
    void stripsMinecraftColors() {
        assertEquals("Hello World", Text.stripColorCodes("§aHello &bWorld"));
    }

    @Test
    void parsesMiniMessage() {
        var component = Text.component("<red>Hello</red>");
        assertEquals("Hello", Text.plain(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void parsesMixedLegacyAndMiniMessage() {
        var component = Text.component("&a<bold>Hello</bold>");
        assertEquals("Hello", Text.plain(component));
        assertEquals(NamedTextColor.GREEN, component.color());
    }

    @Test
    void fallsBackForLegacyAngleBrackets() {
        assertEquals("<player>", Text.plain(Text.component("&a<player>")));
    }

    @Test
    void sanitizesMiniMessageTags() {
        assertEquals("＜red＞hello＜/red＞", Text.sanitize("<red>hello</red>"));
    }

    @Test
    void parsesSuggestCommandClickEvent() {
        var component = Text.component(
                "<click:suggest_command:'/fh unbind'><yellow>/fh unbind</yellow></click>");
        assertEquals("/fh unbind", component.clickEvent().value());
    }
}
