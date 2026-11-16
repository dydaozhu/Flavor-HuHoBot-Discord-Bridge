package com.flavor.huhobot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置文件版本升级。
 *
 * <p>升级时保留用户已经填写的值，只补充新版本缺失的键。
 * 注释无法通过 Bukkit 的 YamlConfiguration 保留，因此升级会重写文件。
 * 不生成 .bak 备份；已有的旧备份会被清理。
 */
final class ConfigUpgrader {

    /** 当前配置文件结构版本，与 config.yml 的 config-version 对应。 */
    private static final int CURRENT_CONFIG_VERSION = 6;

    private ConfigUpgrader() {
    }

    static void upgrade(FlavorDiscordBridge plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        cleanupBackups(plugin);
        ensureMotdSection(plugin, file);

        // 重新读取磁盘文件，确保手动修改的 config-version 能立即生效
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        int from = config.getInt("config-version", 0);
        if (from <= 0) {
            from = 1;
        }

        if (from > CURRENT_CONFIG_VERSION) {
            plugin.getLogger().warning("config-version=" + from
                    + " 高于插件支持的 " + CURRENT_CONFIG_VERSION + "，已跳过自动升级。");
            return;
        }

        boolean legacy = hasLegacyKeys(config);
        if (from == CURRENT_CONFIG_VERSION && !legacy) {
            return;
        }

        String reason = from == CURRENT_CONFIG_VERSION
                ? "检测到旧版配置键"
                : "配置文件版本 v" + from;
        plugin.getLogger().info("正在升级" + reason + " -> v" + CURRENT_CONFIG_VERSION);

        // 先让缺失的新键使用 jar 内 config.yml 的默认值
        try (InputStream defaultsStream = plugin.getResource("config.yml")) {
            if (defaultsStream == null) {
                plugin.getLogger().warning("jar 内缺少 config.yml，无法自动升级配置。");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        } catch (IOException error) {
            plugin.getLogger().warning("读取默认 config.yml 失败，已取消自动升级: " + error.getMessage());
            return;
        }
        config.options().copyDefaults(true);

        if (from < 2 || legacy) {
            migrateToV2(config);
        }
        if (from < 3) {
            migrateToV3(config);
        }
        if (from < 4) {
            migrateToV4(config);
        }
        if (from < 6) {
            migrateToV6(config);
        }

        config.set("config-version", CURRENT_CONFIG_VERSION);
        plugin.saveConfig();
        plugin.reloadConfig();
        plugin.getLogger().info("配置文件已升级到 v" + CURRENT_CONFIG_VERSION + "，已保留原有配置值。");
    }

    private static void cleanupBackups(FlavorDiscordBridge plugin) {
        File[] backups = plugin.getDataFolder().listFiles((dir, name) ->
                name.startsWith("config.yml.v") && name.endsWith(".bak"));
        if (backups == null) {
            return;
        }
        for (File backup : backups) {
            if (backup.delete()) {
                plugin.getLogger().info("已删除旧配置备份: " + backup.getName());
            }
        }
    }

    /**
     * 把 motd 段放到 AI Agent 与 WebUI 之间（security 之前）。
     * 老配置没有该段时插入；已有但位置不对时移动，不改变 config-version。
     */
    private static void ensureMotdSection(FlavorDiscordBridge plugin, File file) {
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            int motd = findTopLevel(lines, "motd:");
            int agent = findTopLevel(lines, "agent:");
            int security = findTopLevel(lines, "security:");
            if (motd >= 0 && agent >= 0 && motd > agent && (security < 0 || motd < security)) {
                return;
            }

            List<String> block = new ArrayList<>();
            if (motd >= 0) {
                int blockStart = motd;
                int removeStart = motd;
                if (removeStart >= 1 && lines.get(removeStart - 1).contains("Discord /motd")) {
                    removeStart--;
                }
                if (removeStart >= 3
                        && lines.get(removeStart - 1).startsWith("# ---")
                        && lines.get(removeStart - 2).contains("Discord /motd")
                        && lines.get(removeStart - 3).startsWith("# ---")) {
                    removeStart -= 3;
                }
                int end = motd + 1;
                while (end < lines.size()) {
                    String line = lines.get(end);
                    if (line.isBlank() || line.startsWith(" ") || line.startsWith("\t")) {
                        end++;
                    } else {
                        break;
                    }
                }
                block.addAll(lines.subList(blockStart, end));
                for (int i = end - 1; i >= removeStart; i--) {
                    lines.remove(i);
                }
            } else {
                block.add("motd:");
                block.add("  default-address: \"mc.hypixel.net\"");
                block.add("");
            }
            block.add(0, "# ------------------------------------------------------------");
            block.add(1, "# Discord /motd 不填地址时查询的服务器");
            block.add(2, "# ------------------------------------------------------------");

            int insert = findTopLevel(lines, "security:");
            if (insert < 0) {
                insert = findTopLevel(lines, "webui:");
            }
            if (insert < 0) {
                lines.addAll(block);
            } else {
                lines.addAll(insert, block);
            }
            Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
            plugin.getLogger().info("已将 motd 配置迁移到 AI Agent 与 WebUI 之间");
        } catch (Exception error) {
            plugin.getLogger().warning("迁移 motd 配置失败: " + error.getMessage());
        }
    }

    private static int findTopLevel(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String clean = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (clean.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasLegacyKeys(FileConfiguration config) {
        return config.contains("discord.channels.chat")
                || config.contains("bridge.discord-to-game")
                || config.contains("bridge.game-to-discord")
                || config.contains("link.code-ttl-seconds");
    }

    /**
     * v1 -> v2：
     * - 旧的单个 discord.channels.chat 拆成 game-to-discord / discord-to-game
     * - 删除已废弃的 bridge.discord-to-game / bridge.game-to-discord / link.code-ttl-seconds
     */
    private static void migrateToV2(FileConfiguration config) {
        String chat = config.getString("discord.channels.chat", "");
        boolean discordToGame = config.getBoolean("bridge.discord-to-game", true);
        boolean gameToDiscord = config.getBoolean("bridge.game-to-discord", true);

        if (chat != null && !chat.isBlank()) {
            if (discordToGame && config.getString("discord.channels.discord-to-game", "").isBlank()) {
                config.set("discord.channels.discord-to-game", chat);
            }
            if (gameToDiscord && config.getString("discord.channels.game-to-discord", "").isBlank()) {
                config.set("discord.channels.game-to-discord", chat);
            }
        }

        config.set("discord.channels.chat", null);
        config.set("bridge.discord-to-game", null);
        config.set("bridge.game-to-discord", null);
        config.set("link.code-ttl-seconds", null);
    }

    /**
     * v2 -> v3：
     * - 默认改为 blacklist
     * - 清空 allowed-commands，但保留 blocked-commands
     */
    private static void migrateToV3(FileConfiguration config) {
        config.set("security.command-mode", "blacklist");
        config.set("security.allowed-commands", List.of());
    }

    /**
     * v3 -> v4：新增可配置的白名单查询命令。
     */
    private static void migrateToV4(FileConfiguration config) {
        if (config.getString("link.whitelist-list-command", "").isBlank()) {
            config.set("link.whitelist-list-command", "whitelist list");
        }
    }

    /** v5 -> v6：新增每个 Discord 用户的最大绑定数。 */
    private static void migrateToV6(FileConfiguration config) {
        if (!config.contains("link.max-accounts")) {
            config.set("link.max-accounts", 1);
        }
    }
}
