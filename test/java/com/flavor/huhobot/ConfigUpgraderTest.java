package com.flavor.huhobot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpgraderTest {

    @Test
    void rewritesV7ConfigIntoV8LayoutAndKeepsValues() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.set("config-version", 7);
        old.set("language", "zh_CN");
        old.set("server.name", "My Server");
        old.set("server.host", "play.example.com");
        old.set("discord.token", "token-value");
        old.set("discord.guild-id", "guild-1");
        old.set("discord.channels.game-to-discord", "channel-game");
        old.set("discord.channels.discord-to-game", "channel-discord");
        old.set("discord.channels.agent", "channel-agent");
        old.set("discord.channels.audit", "channel-audit");
        old.set("bridge.from-game", "[G] {name}: {message}");
        old.set("bridge.from-discord", "[D] {name}: {message}");
        old.set("bridge.start-with", "!");
        old.set("bridge.events.join", false);
        old.set("bridge.events.quit-format", "[Q] {name}");
        old.set("link.whitelist-on-link", true);
        old.set("link.max-accounts", 3);
        old.set("link.whitelist-list-command", "whitelist list");
        old.set("binding.force-bind-groups", java.util.List.of("https://discord.gg/test"));
        old.set("agent.enabled", true);
        old.set("agent.model", "test-model");
        old.set("motd.default-address", "mc.example.com");

        String rewritten;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertTrue(stream != null);
            rewritten = ConfigUpgrader.buildV8Config(stream, old);
        }

        YamlConfiguration result = new YamlConfiguration();
        result.options().parseComments(true);
        result.load(new StringReader(rewritten));

        assertEquals(8, result.getInt("config-version"));
        assertEquals("My Server", result.getString("server.name"));
        assertEquals("guild-1", result.getString("discord.server-id"));
        assertEquals("channel-game", result.getString("bridge.channels.game-to-discord"));
        assertEquals("channel-agent", result.getString("agent.agent"));
        assertEquals("[G] {name}: {message}", result.getString("bridge.message.game-message"));
        assertTrue(result.getBoolean("bridge.message.game-enabled"));
        assertFalse(result.getBoolean("bridge.message.join-enabled"));
        assertEquals("[Q] {name}", result.getString("bridge.message.quit-message"));
        assertTrue(result.getBoolean("bind.whitelist-on-bind"));
        assertEquals(3, result.getInt("bind.max-accounts"));
        assertEquals("https://discord.gg/test", result.getStringList("binding.links").get(0));
        assertFalse(result.contains("discord.guild-id"));
        assertFalse(result.contains("link.whitelist-on-link"));
        assertTrue(rewritten.contains("# 服务器信息"));
        assertTrue(rewritten.indexOf("\n  token:") < rewritten.indexOf("\n  server-id:"));
    }

    @Test
    void keepsTokenPositionWhenTokenIsEmpty() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.set("config-version", 7);
        old.set("discord.token", "");
        old.set("discord.guild-id", "guild-1");

        String rewritten;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertTrue(stream != null);
            rewritten = ConfigUpgrader.buildV8Config(stream, old);
        }

        int token = rewritten.indexOf("\n  token:");
        int serverId = rewritten.indexOf("\n  server-id:");
        assertTrue(token >= 0);
        assertTrue(token < serverId);
        assertTrue(rewritten.substring(token).startsWith("\n  token:\n"));
    }

    @Test
    void movesTokenBackBeforeServerId() {
        String broken = """
                discord:
                  # 创建应用后获取
                  server-id: "123"
                  admin-role-ids: []
                  intents:
                    members: true
                  token: "secret"
                """;
        String fixed = ConfigUpgrader.normalizeTokenOrder(broken);
        assertTrue(fixed.indexOf("token:") < fixed.indexOf("server-id:"));
    }
}
