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
 * <p>升级时保留用户已经填写的值。v8 使用 jar 内模板重排配置，并保留模板注释与结构。
 * 不生成 .bak 备份；已有的旧备份会被清理。
 */
final class ConfigUpgrader {

    /** 当前配置文件结构版本，与 config.yml 的 config-version 对应。 */
    private static final int CURRENT_CONFIG_VERSION = 8;

    private ConfigUpgrader() {
    }

    static void upgrade(FlavorDiscordBridge plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        cleanupBackups(plugin);
        ensureServerSection(plugin, file);
        ensureMotdSection(plugin, file);
        ensureTokenOrder(plugin, file);

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

        // v8 是完整重排版本：以 jar 内新模板为骨架，迁移旧配置值。
        if (from < 8) {
            migrateToV8(plugin, file, config);
            return;
        }
        if (from == CURRENT_CONFIG_VERSION) {
            if (needsV8Refresh(config)) {
                migrateToV8(plugin, file, config);
            }
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
        if (from < 7) {
            migrateToV7(config);
        }

        config.set("config-version", CURRENT_CONFIG_VERSION);
        plugin.saveConfig();
        plugin.reloadConfig();
        plugin.getLogger().info("配置文件已升级到 v" + CURRENT_CONFIG_VERSION + "，已保留原有配置值。");
    }

    /**
     * 修正旧版重排时被移到 discord 段末尾的 token，使其回到 server-id 之前。
     */
    private static void ensureTokenOrder(FlavorDiscordBridge plugin, File file) {
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String updated = normalizeTokenOrder(text)
                    .replace("  token: \"\"\n", "  token:\n")
                    .replace("  token: ''\n", "  token:\n");
            if (!updated.equals(text)) {
                Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
                plugin.getLogger().info("已修正 discord.token 的配置位置");
            }
        } catch (Exception error) {
            plugin.getLogger().warning("修正 token 配置位置失败: " + error.getMessage());
        }
    }

    static String normalizeTokenOrder(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        int discord = findTopLevel(lines, "discord:");
        if (discord < 0) {
            return text;
        }
        int token = -1;
        int serverId = -1;
        for (int i = discord + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(" ") && !line.startsWith("\t") && !line.isBlank()) {
                break;
            }
            if (token < 0 && line.trim().startsWith("token:")) {
                token = i;
            }
            if (serverId < 0 && line.trim().startsWith("server-id:")) {
                serverId = i;
            }
        }
        if (token < 0 || serverId < 0 || token < serverId) {
            return text;
        }

        String tokenLine = lines.remove(token);
        int insert = serverId;
        for (int i = discord + 1; i < insert; i++) {
            if (lines.get(i).contains("# 机器人所在服务器 ID")) {
                insert = i;
                break;
            }
        }
        lines.add(insert, tokenLine);
        return String.join("\n", lines);
    }

    /**
     * v7 -> v8：按新模板完整重排配置，并迁移旧配置中的值。
     */
    private static void migrateToV8(FlavorDiscordBridge plugin, File file, FileConfiguration source) {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("jar 内缺少 config.yml，无法重排配置。");
                return;
            }
            Files.writeString(file.toPath(), buildV8Config(stream, source), StandardCharsets.UTF_8);
            plugin.reloadConfig();
            plugin.getLogger().info("配置文件已重排为 v8 格式，原有配置值已迁移。");
        } catch (Exception error) {
            plugin.getLogger().warning("重排 v8 配置失败: " + error.getMessage());
        }
    }

    static String buildV8Config(InputStream stream, FileConfiguration source) throws Exception {
        YamlConfiguration target = new YamlConfiguration();
        target.options().parseComments(true);
        target.load(new InputStreamReader(stream, StandardCharsets.UTF_8));

        copy(source, target, "language");
        copy(source, target, "server.name");
        copy(source, target, "server.host");

        copy(source, target, "discord.token");
        copyFirst(source, target, "discord.server-id", "discord.guild-id");
        copy(source, target, "discord.admin-role-ids");
        copy(source, target, "discord.intents.message-content");
        copy(source, target, "discord.intents.members");

        copy(source, target, "bridge.enabled");
        copyFirst(source, target, "bridge.channels.game-to-discord", "discord.channels.game-to-discord");
        copyFirst(source, target, "bridge.channels.discord-to-game", "discord.channels.discord-to-game");
        copyFirst(source, target, "bridge.channels.audit", "discord.channels.audit");
        copy(source, target, "bridge.message.game-message", "bridge.from-game");
        copy(source, target, "bridge.message.discord-message", "bridge.from-discord");
        copy(source, target, "bridge.message.join-message", "bridge.events.join-format");
        copy(source, target, "bridge.message.quit-message", "bridge.events.quit-format");
        target.set("bridge.message.game-enabled",
                source.contains("bridge.message.game-enabled")
                        ? source.getBoolean("bridge.message.game-enabled", true)
                        : !target.getString("bridge.message.game-message", "").isBlank());
        target.set("bridge.message.discord-enabled",
                source.contains("bridge.message.discord-enabled")
                        ? source.getBoolean("bridge.message.discord-enabled", true)
                        : !target.getString("bridge.message.discord-message", "").isBlank());
        target.set("bridge.message.join-enabled",
                source.contains("bridge.message.join-enabled")
                        ? source.getBoolean("bridge.message.join-enabled", true)
                        : source.contains("bridge.events.join")
                                ? source.getBoolean("bridge.events.join", true)
                                : !target.getString("bridge.message.join-message", "").isBlank());
        target.set("bridge.message.quit-enabled",
                source.contains("bridge.message.quit-enabled")
                        ? source.getBoolean("bridge.message.quit-enabled", true)
                        : source.contains("bridge.events.quit")
                                ? source.getBoolean("bridge.events.quit", true)
                                : !target.getString("bridge.message.quit-message", "").isBlank());
        copyFirst(source, target, "bridge.game-start-with", "bridge.start-with");
        copy(source, target, "bridge.discord-start-with");
        copy(source, target, "bridge.ignore-bots");
        copy(source, target, "bridge.link-required");

        copyFirst(source, target, "bind.whitelist-on-bind", "link.whitelist-on-link");
        copyFirst(source, target, "bind.max-accounts", "link.max-accounts");
        copyFirst(source, target, "bind.whitelist-add-command", "link.whitelist-add-command");
        copyFirst(source, target, "bind.whitelist-remove-command", "link.whitelist-remove-command");
        copyFirst(source, target, "bind.whitelist-list-command", "link.whitelist-list-command");

        copy(source, target, "binding.force-bind");
        copyFirst(source, target, "binding.links", "binding.force-bind-groups");
        copy(source, target, "binding.verify-exempt");

        copy(source, target, "agent.enabled");
        copy(source, target, "agent.base-url");
        copy(source, target, "agent.api-key");
        copy(source, target, "agent.model");
        copy(source, target, "agent.mode");
        copy(source, target, "agent.max-steps");
        copy(source, target, "agent.max-context-tokens");
        copy(source, target, "agent.daily-token-budget");
        copy(source, target, "agent.allow-self-approval");
        copy(source, target, "agent.approval-timeout-seconds");
        copy(source, target, "agent.temperature");
        copyFirst(source, target, "agent.agent", "discord.channels.agent");

        copy(source, target, "motd.default-address");
        copy(source, target, "security.command-mode");
        copy(source, target, "security.allowed-commands");
        copy(source, target, "security.blocked-commands");
        copy(source, target, "security.max-command-length");
        copy(source, target, "webui.enabled");
        copy(source, target, "webui.bind");
        copy(source, target, "webui.port");

        target.set("config-version", CURRENT_CONFIG_VERSION);
        String yaml = target.saveToString();
        // 空 token 保持目标格式的空白值，同时保留它在 discord 段中的原始位置。
        return yaml.replace("  token: ''\n", "  token:\n");
    }

    private static boolean needsV8Refresh(FileConfiguration config) {
        return !config.contains("bridge.message.game-enabled")
                || !config.contains("bridge.message.discord-enabled")
                || !config.contains("bridge.message.join-enabled")
                || !config.contains("bridge.message.quit-enabled")
                || config.contains("discord.guild-id")
                || config.contains("bridge.from-game")
                || config.contains("link.whitelist-on-link");
    }

    private static void copy(FileConfiguration source, YamlConfiguration target, String path) {
        if (source.contains(path)) {
            target.set(path, source.get(path));
        }
    }

    private static void copy(FileConfiguration source, YamlConfiguration target,
                             String targetPath, String sourcePath) {
        if (source.contains(targetPath)) {
            target.set(targetPath, source.get(targetPath));
        } else if (source.contains(sourcePath)) {
            target.set(targetPath, source.get(sourcePath));
        }
    }

    private static void copyFirst(FileConfiguration source, YamlConfiguration target,
                                  String targetPath, String sourcePath) {
        if (source.contains(targetPath)) {
            target.set(targetPath, source.get(targetPath));
        } else if (source.contains(sourcePath)) {
            target.set(targetPath, source.get(sourcePath));
        }
    }

    private static void copyJoinMessage(FileConfiguration source, YamlConfiguration target,
                                        String targetPath, String formatPath, String enabledPath) {
        if (source.contains(enabledPath) && !source.getBoolean(enabledPath, true)) {
            target.set(targetPath, "");
        } else if (source.contains(formatPath)) {
            target.set(targetPath, source.get(formatPath));
        }
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
     * 在 Discord 机器人配置块之前插入服务器信息段。
     * 老配置没有 server 段时只插入一次，不覆盖已有值。
     */
    private static void ensureServerSection(FlavorDiscordBridge plugin, File file) {
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            if (findTopLevel(lines, "server:") >= 0) {
                // 修复旧版本在 server 段前多插入的一条分隔线。
                for (int i = 2; i < lines.size(); i++) {
                    if (lines.get(i).contains("# 服务器信息")
                            && lines.get(i - 1).startsWith("# ---")
                            && lines.get(i - 2).startsWith("# ---")) {
                        lines.remove(i - 2);
                        Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
                        break;
                    }
                }
                return;
            }
            List<String> block = new ArrayList<>(List.of(
                    "# ------------------------------------------------------------",
                    "# 服务器信息",
                    "# ------------------------------------------------------------",
                    "server:",
                    "  name: \"Flavor-HuHoBot-Discord-Bridge\"",
                    "  host: \"huhobot.mcfwq.top\"",
                    ""
            ));

            int insert = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("# Discord 机器人")) {
                    insert = i;
                    if (insert >= 1 && lines.get(insert - 1).startsWith("# ---")) {
                        insert--;
                    }
                    break;
                }
            }
            if (insert < 0) {
                insert = 0;
            }
            lines.addAll(insert, block);
            Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
            plugin.getLogger().info("已插入服务器信息配置段");
        } catch (Exception error) {
            plugin.getLogger().warning("迁移服务器信息配置失败: " + error.getMessage());
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

    /** v6 -> v7：新增服务器名称与地址。 */
    private static void migrateToV7(FileConfiguration config) {
        if (!config.contains("server.name")) {
            config.set("server.name", "Flavor-HuHoBot-Discord-Bridge");
        }
        if (!config.contains("server.host")) {
            config.set("server.host", "huhobot.mcfwq.top");
        }
    }
}
