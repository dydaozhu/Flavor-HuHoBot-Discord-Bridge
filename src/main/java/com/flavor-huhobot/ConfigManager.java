package com.flavor.huhobot;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/** 配置访问层，所有路径都集中在这里，避免业务代码到处写字符串。 */
public final class ConfigManager {

    private final FlavorDiscordBridge plugin;
    private FileConfiguration config;

    public ConfigManager(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    public String language() {
        return config.getString("language", "zh_CN");
    }

    // ------------------------------------------------------------------ Server

    public String serverName() {
        return config.getString("server.name", "Flavor-HuHoBot-Discord-Bridge");
    }

    public String serverHost() {
        return config.getString("server.host", "huhobot.mcfwq.top");
    }

    // ------------------------------------------------------------------ Discord

    public String token() {
        return value("discord.token");
    }

    public String guildId() {
        return first("discord.server-id", "discord.guild-id");
    }

    public String gameToDiscordChannelId() {
        return first("bridge.channels.game-to-discord", "discord.channels.game-to-discord");
    }

    public String discordToGameChannelId() {
        return first("bridge.channels.discord-to-game", "discord.channels.discord-to-game");
    }

    public String agentChannelId() {
        return first("agent.agent", "discord.channels.agent");
    }

    public String auditChannelId() {
        return first("bridge.channels.audit", "discord.channels.audit");
    }

    public List<String> adminRoleIds() {
        return config.getStringList("discord.admin-role-ids");
    }

    public boolean messageContentIntent() {
        return config.getBoolean("discord.intents.message-content", true);
    }

    public boolean membersIntent() {
        return config.getBoolean("discord.intents.members", true);
    }

    // ------------------------------------------------------------------ Bridge

    public boolean bridgeEnabled() {
        return config.getBoolean("bridge.enabled", true);
    }

    public String fromGame() {
        return first("bridge.message.game-message", "bridge.from-game");
    }

    public boolean gameMessageEnabled() {
        if (config.contains("bridge.message.game-enabled")) {
            return config.getBoolean("bridge.message.game-enabled", true) && !fromGame().isBlank();
        }
        return !fromGame().isBlank();
    }

    public String fromDiscord() {
        return first("bridge.message.discord-message", "bridge.from-discord");
    }

    public boolean discordMessageEnabled() {
        if (config.contains("bridge.message.discord-enabled")) {
            return config.getBoolean("bridge.message.discord-enabled", true) && !fromDiscord().isBlank();
        }
        return !fromDiscord().isBlank();
    }

    public boolean ignoreBots() {
        return config.getBoolean("bridge.ignore-bots", true);
    }

    public String startWith() {
        return first("bridge.game-start-with", "bridge.start-with");
    }

    public String discordStartWith() {
        return value("bridge.discord-start-with");
    }

    public boolean linkRequiredForChat() {
        return config.getBoolean("bridge.link-required", false);
    }

    public boolean joinEnabled() {
        if (config.contains("bridge.message.join-enabled")) {
            return config.getBoolean("bridge.message.join-enabled", true) && !joinFormat().isBlank();
        }
        if (config.contains("bridge.events.join")) {
            return config.getBoolean("bridge.events.join", true);
        }
        return !joinFormat().isBlank();
    }

    public boolean quitEnabled() {
        if (config.contains("bridge.message.quit-enabled")) {
            return config.getBoolean("bridge.message.quit-enabled", true) && !quitFormat().isBlank();
        }
        if (config.contains("bridge.events.quit")) {
            return config.getBoolean("bridge.events.quit", true);
        }
        return !quitFormat().isBlank();
    }

    public String joinFormat() {
        return first("bridge.message.join-message", "bridge.events.join-format");
    }

    public String quitFormat() {
        return first("bridge.message.quit-message", "bridge.events.quit-format");
    }

    // ------------------------------------------------------------------ Bind

    public boolean whitelistOnLink() {
        if (config.contains("bind.whitelist-on-bind")) {
            return config.getBoolean("bind.whitelist-on-bind", false);
        }
        return config.getBoolean("link.whitelist-on-link", true);
    }

    public int linkMaxAccounts() {
        return Math.max(1, config.getInt("bind.max-accounts",
                config.getInt("link.max-accounts", 1)));
    }

    public String whitelistAddCommand() {
        return first("bind.whitelist-add-command", "link.whitelist-add-command");
    }

    public String whitelistRemoveCommand() {
        return first("bind.whitelist-remove-command", "link.whitelist-remove-command");
    }

    public String whitelistListCommand() {
        return first("bind.whitelist-list-command", "link.whitelist-list-command");
    }

    public String motdDefaultAddress() {
        return config.getString("motd.default-address", "mc.hypixel.net");
    }

    // ------------------------------------------------------------------ Binding

    public boolean bindingForceBind() {
        return config.getBoolean("binding.force-bind", false);
    }

    public List<String> bindingForceBindGroups() {
        List<String> links = config.getStringList("binding.links");
        return links.isEmpty() ? config.getStringList("binding.force-bind-groups") : links;
    }

    public List<String> bindingVerifyExempt() {
        return config.getStringList("binding.verify-exempt");
    }

    // ------------------------------------------------------------------ Agent

    public boolean agentEnabled() {
        return config.getBoolean("agent.enabled", false);
    }

    public String agentBaseUrl() {
        return value("agent.base-url");
    }

    public String agentApiKey() {
        return value("agent.api-key");
    }

    public String agentModel() {
        String model = value("agent.model");
        return model.isBlank() ? "gpt-4o-mini" : model;
    }

    public String agentMode() {
        return config.getString("agent.mode", "manual").toLowerCase(Locale.ROOT);
    }

    public boolean agentManualApproval() {
        return !"auto".equals(agentMode());
    }

    public int agentMaxSteps() {
        return Math.min(30, Math.max(1, config.getInt("agent.max-steps", 15)));
    }

    public int agentMaxContextTokens() {
        return Math.max(4000, config.getInt("agent.max-context-tokens", 24000));
    }

    public long agentDailyTokenBudget() {
        return config.getLong("agent.daily-token-budget", 200000L);
    }

    public boolean allowSelfApproval() {
        return config.getBoolean("agent.allow-self-approval", false);
    }

    public int approvalTimeoutSeconds() {
        return Math.max(30, config.getInt("agent.approval-timeout-seconds", 300));
    }

    public double agentTemperature() {
        return config.getDouble("agent.temperature", 0.2D);
    }

    // ------------------------------------------------------------------ Security

    public String commandMode() {
        return config.getString("security.command-mode", "blacklist").trim().toLowerCase(Locale.ROOT);
    }

    public List<String> allowedCommands() {
        return config.getStringList("security.allowed-commands");
    }

    public List<String> blockedCommands() {
        return config.getStringList("security.blocked-commands");
    }

    public int maxCommandLength() {
        return Math.max(32, config.getInt("security.max-command-length", 256));
    }

    // ------------------------------------------------------------------ WebUI

    public boolean webuiEnabled() {
        return config.getBoolean("webui.enabled", true);
    }

    public String webuiBind() {
        return config.getString("webui.bind", "127.0.0.1");
    }

    public int webuiPort() {
        return config.getInt("webui.port", 8080);
    }

    // ------------------------------------------------------------------ 写入

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public void save() {
        plugin.saveConfig();
    }

    private String value(String path) {
        String raw = config.getString(path, "");
        return raw == null ? "" : raw.trim();
    }

    /** 优先读取新路径，缺失时兼容旧路径。 */
    private String first(String path, String legacyPath) {
        if (config.contains(path)) {
            return value(path);
        }
        return value(legacyPath);
    }
}
