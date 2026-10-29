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

    // ------------------------------------------------------------------ Discord

    public String token() {
        return value("discord.token");
    }

    public String guildId() {
        return value("discord.guild-id");
    }

    public String gameToDiscordChannelId() {
        return value("discord.channels.game-to-discord");
    }

    public String discordToGameChannelId() {
        return value("discord.channels.discord-to-game");
    }

    public String agentChannelId() {
        return value("discord.channels.agent");
    }

    public String auditChannelId() {
        return value("discord.channels.audit");
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
        return config.getString("bridge.from-game", "[MC] {name}: {message}");
    }

    public String fromDiscord() {
        return config.getString("bridge.from-discord", "[DC] {name}: {message}");
    }

    public boolean ignoreBots() {
        return config.getBoolean("bridge.ignore-bots", true);
    }

    public String startWith() {
        return config.getString("bridge.start-with", "");
    }

    public boolean linkRequiredForChat() {
        return config.getBoolean("bridge.link-required", false);
    }

    public boolean joinEnabled() {
        return config.getBoolean("bridge.events.join", true);
    }

    public boolean quitEnabled() {
        return config.getBoolean("bridge.events.quit", true);
    }

    public String joinFormat() {
        return config.getString("bridge.events.join-format", "[MC] {name} 加入了服务器");
    }

    public String quitFormat() {
        return config.getString("bridge.events.quit-format", "[MC] {name} 离开了服务器");
    }

    // ------------------------------------------------------------------ Link

    public boolean whitelistOnLink() {
        return config.getBoolean("link.whitelist-on-link", true);
    }

    public String whitelistAddCommand() {
        return config.getString("link.whitelist-add-command", "whitelist add {player}");
    }

    public String whitelistRemoveCommand() {
        return config.getString("link.whitelist-remove-command", "whitelist remove {player}");
    }

    public String whitelistListCommand() {
        return config.getString("link.whitelist-list-command", "whitelist list");
    }

    // ------------------------------------------------------------------ Binding

    public boolean bindingForceBind() {
        return config.getBoolean("binding.force-bind", false);
    }

    public List<String> bindingForceBindGroups() {
        return config.getStringList("binding.force-bind-groups");
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
}
