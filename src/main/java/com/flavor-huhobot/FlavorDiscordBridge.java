package com.flavor.huhobot;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/** 插件主类。 */
public final class FlavorDiscordBridge extends JavaPlugin {

    private ConfigManager configManager;
    private Messages messages;
    private LinkStore links;
    private AuditLog audit;
    private UsageStore usage;
    private volatile CommandPolicy commandPolicy;
    private LinkService linkService;
    private ForceBindService forceBindService;
    private ChatBridge chatBridge;
    private DiscordBot discordBot;
    private AgentService agentService;
    private WebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            getLogger().severe("无法创建插件数据目录: " + getDataFolder());
        }
        ConfigUpgrader.upgrade(this);

        configManager = new ConfigManager(this);
        messages = new Messages(this);
        messages.load();

        links = new LinkStore(resolveBindsFile());
        links.load();
        audit = new AuditLog(new File(getDataFolder(), "audit.jsonl"));
        usage = new UsageStore(new File(getDataFolder(), "usage.json"));
        usage.load();
        commandPolicy = new CommandPolicy(configManager);

        linkService = new LinkService(this);
        forceBindService = new ForceBindService(this);
        chatBridge = new ChatBridge(this);
        discordBot = new DiscordBot(this);
        agentService = new AgentService(this);
        webServer = new WebServer(this);

        if (getCommand("flavorhuhobot") != null) {
            DiscordCommand executor = new DiscordCommand(this);
            getCommand("flavorhuhobot").setExecutor(executor);
            getCommand("flavorhuhobot").setTabCompleter(executor);
        } else {
            getLogger().severe("plugin.yml 中缺少 flavorhuhobot 命令定义。");
        }
        getServer().getPluginManager().registerEvents(new McChatListener(this), this);

        webServer.start();
        discordBot.startAsync();

        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> forceBindService.cleanup(), 1200L, 1200L);

        if (configManager.bindingForceBind() && getServer().hasWhitelist()) {
            getLogger().warning("binding.force-bind 已开启，但服务器 whitelist 仍为开启状态；");
            getLogger().warning("未绑定玩家会在登录阶段被白名单拦截，根本拿不到验证码。");
            getLogger().warning("请关闭 Minecraft 自带白名单/其他白名单插件，或改用其他入服流程。");
        }
        if (configManager.bindingForceBind()) {
            getLogger().info("强制绑定已开启：未绑定玩家进服获取 5 位验证码后，在 Discord 使用 /bind <验证码>。");
        }

        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            getLogger().warning("检测到 DiscordSRV。若两个插件都启用了聊天桥接，会出现重复转发；");
            getLogger().warning("请在 config.yml 中把 bridge.enabled 设为 false，只使用本插件的 AI/管理能力。");
        }

        getLogger().info("Flavor-HuHoBot-Discord-Bridge 已加载（Java 21 / Paper 1.21+）");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stop();
        }
        if (webServer != null) {
            webServer.stop();
        }
        if (agentService != null) {
            agentService.shutdown();
        }
        if (links != null) {
            links.save();
        }
        if (usage != null) {
            usage.save();
        }
    }

    /** 重载配置、语言、绑定/用量数据，并重新连接 Discord。 */
    public void reloadAll() {
        // 手动修改 config-version 后，/reload 也要重新检查并迁移
        ConfigUpgrader.upgrade(this);
        configManager.reload();
        messages.load();
        links.load();
        usage.load();
        commandPolicy = new CommandPolicy(configManager);
        discordBot.restart();
        getLogger().info("配置、语言、绑定与用量数据已重载，Discord 正在重新连接。");
    }

    /**
     * 绑定数据文件从 links.json 迁移到 binds.json。
     * 如果迁移失败则继续使用旧文件，避免丢失绑定数据。
     */
    private File resolveBindsFile() {
        File binds = new File(getDataFolder(), "binds.json");
        File legacy = new File(getDataFolder(), "links.json");
        if (binds.isFile() || !legacy.isFile()) {
            return binds;
        }
        try {
            Files.move(legacy.toPath(), binds.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("已将 links.json 迁移为 binds.json");
            return binds;
        } catch (IOException moveError) {
            try {
                Files.copy(legacy.toPath(), binds.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(legacy.toPath());
                getLogger().info("已将 links.json 复制并迁移为 binds.json");
                return binds;
            } catch (IOException copyError) {
                getLogger().severe("迁移 links.json 到 binds.json 失败: " + copyError.getMessage());
                return legacy;
            }
        }
    }

    public ConfigManager config() {
        return configManager;
    }

    public Messages messages() {
        return messages;
    }

    public LinkStore links() {
        return links;
    }

    public AuditLog audit() {
        return audit;
    }

    public UsageStore usage() {
        return usage;
    }

    public CommandPolicy commandPolicy() {
        return commandPolicy;
    }

    public LinkService linkService() {
        return linkService;
    }

    public ForceBindService forceBindService() {
        return forceBindService;
    }

    public ChatBridge chatBridge() {
        return chatBridge;
    }

    public DiscordBot discordBot() {
        return discordBot;
    }

    public AgentService agentService() {
        return agentService;
    }

    public WebServer webServer() {
        return webServer;
    }
}
