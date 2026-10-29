package com.flavor.huhobot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** JDA 生命周期与 Guild/频道/成员访问。 */
public final class DiscordBot {

    private final FlavorDiscordBridge plugin;
    private final MessageSender sender;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger startAttempts = new java.util.concurrent.atomic.AtomicInteger();

    private volatile JDA jda;
    private volatile boolean shutdown;

    public DiscordBot(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
        this.sender = new MessageSender(plugin);
    }

    public MessageSender sender() {
        return sender;
    }

    public JDA jda() {
        return jda;
    }

    public boolean isReady() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public String selfId() {
        JDA current = jda;
        return current == null ? "" : current.getSelfUser().getId();
    }

    public void startAsync() {
        shutdown = false;
        startAttempts.set(0);
        int gen = generation.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> start(gen));
    }

    private void start(int gen) {
        if (shutdown || gen != generation.get()) {
            return;
        }
        if (!starting.compareAndSet(false, true)) {
            // 旧的启动任务还没结束（例如 reload 时 JDA 正在 awaitReady），稍后再试
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                    plugin, () -> start(gen), 20L);
            return;
        }
        try {
            String token = plugin.config().token();
            if (token.isBlank()) {
                plugin.getLogger().warning("未配置 discord.token，Discord 机器人不会启动。");
                return;
            }
            JDABuilder builder = JDABuilder.createDefault(token);
            builder.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
            if (plugin.config().membersIntent()) {
                builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
            }
            builder.enableIntents(GatewayIntent.GUILD_MODERATION);
            builder.setActivity(Activity.playing("Minecraft"));
            builder.addEventListeners(
                    new DiscordListener(plugin),
                    new SlashCommands(plugin),
                    new ButtonListener(plugin)
            );

            JDA built = builder.build();
            // 先赋值，保证 Gateway 事件在 awaitReady 返回前也能拿到实例
            this.jda = built;
            built.awaitReady();
            if (shutdown || gen != generation.get()) {
                built.shutdownNow();
                return;
            }
            registerCommands(built);
            checkChannelPermissions();
            startAttempts.set(0);
            plugin.getLogger().info("Discord 已连接: " + built.getSelfUser().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retry(gen, "启动被中断");
        } catch (Throwable error) {
            plugin.getLogger().severe("Discord 启动失败: " + error.getMessage());
            retry(gen, error.getMessage());
        } finally {
            starting.set(false);
        }
    }

    /** 启动失败时自动重试，避免必须手动 reload 才能连上 Discord。 */
    private void retry(int gen, String reason) {
        JDA failed = jda;
        jda = null;
        if (failed != null) {
            failed.shutdownNow();
        }
        if (shutdown || gen != generation.get()) {
            return;
        }
        int attempt = startAttempts.incrementAndGet();
        if (attempt > 5) {
            plugin.getLogger().severe("Discord 连续启动失败，已停止重试。请检查 token、网络和 Discord 状态。");
            return;
        }
        long seconds = Math.min(30, 5L * attempt);
        plugin.getLogger().warning("Discord 连接失败（" + reason + "），" + seconds
                + " 秒后自动重试（" + attempt + "/5）。");
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                plugin, () -> start(gen), seconds * 20L);
    }

    private void registerCommands(JDA jda) {
        registerCommands(jda, 0);
    }

    private void registerCommands(JDA jda, int attempt) {
        if (shutdown || this.jda != jda || jda.getStatus() != JDA.Status.CONNECTED) {
            return;
        }
        List<net.dv8tion.jda.api.interactions.commands.build.CommandData> commands = SlashCommands.definitions(plugin);
        Guild guild = guild();
        if (guild == null && !plugin.config().guildId().isBlank()) {
            if (attempt < 3) {
                plugin.getLogger().warning("尚未找到配置的 Discord 服务器，稍后重试注册 Slash 命令…");
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> registerCommands(jda, attempt + 1), 100L);
            } else {
                plugin.getLogger().severe("找不到 discord.guild-id 对应的服务器，Slash 命令未注册。");
            }
            return;
        }
        CommandListUpdateAction action = guild != null ? guild.updateCommands() : jda.updateCommands();
        action.addCommands(commands).queue(
                ok -> plugin.getLogger().info("已注册 " + commands.size() + " 条 Slash 命令"),
                error -> {
                    plugin.getLogger().severe("注册 Slash 命令失败: " + error.getMessage());
                    if (attempt < 3 && !shutdown && this.jda == jda) {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> registerCommands(jda, attempt + 1), 100L);
                    }
                }
        );
    }

    public Guild guild() {
        JDA current = jda;
        if (current == null) {
            return null;
        }
        String id = plugin.config().guildId();
        if (!id.isBlank()) {
            return current.getGuildById(id);
        }
        List<Guild> guilds = current.getGuilds();
        return guilds.size() == 1 ? guilds.get(0) : null;
    }

    public TextChannel textChannel(String id) {
        JDA current = jda;
        if (current == null || id == null || id.isBlank()) {
            return null;
        }
        return current.getTextChannelById(id);
    }

    /**
     * 文本频道或线程频道。AI 会话在线程里，必须用这个而不是 {@link #textChannel(String)}，
     * 否则线程消息会被当成“找不到频道”丢弃。
     */
    public MessageChannel messageChannel(String id) {
        JDA current = jda;
        if (current == null || id == null || id.isBlank()) {
            return null;
        }
        TextChannel text = current.getTextChannelById(id);
        if (text != null) {
            return text;
        }
        return current.getThreadChannelById(id);
    }

    /** 启动时检查聊天/Agent 频道是否给了机器人发送消息与 Embed 权限。 */
    private void checkChannelPermissions() {
        Guild guild = guild();
        if (guild == null) {
            return;
        }
        Member self = guild.getSelfMember();
        for (String id : List.of(plugin.config().gameToDiscordChannelId(), plugin.config().agentChannelId())) {
            if (id.isBlank()) {
                continue;
            }
            TextChannel channel = textChannel(id);
            if (channel == null) {
                continue;
            }
            if (!self.hasPermission(channel, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) {
                plugin.getLogger().warning("机器人在频道 #" + channel.getName()
                        + " 缺少 发送消息/嵌入链接 权限，请检查频道权限覆盖。");
            }
        }
        String listenId = plugin.config().discordToGameChannelId();
        if (!listenId.isBlank()) {
            TextChannel channel = textChannel(listenId);
            if (channel != null && !self.hasPermission(channel, Permission.VIEW_CHANNEL)) {
                plugin.getLogger().warning("机器人在频道 #" + channel.getName()
                        + " 缺少 查看频道 权限，Discord -> 游戏 方向无法工作。");
            }
        }
    }

    public Member member(Guild guild, String id) {
        if (guild == null || id == null || id.isBlank()) {
            return null;
        }
        Member member = guild.getMemberById(id);
        if (member != null) {
            return member;
        }
        try {
            return guild.retrieveMemberById(id).complete();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isAdmin(Member member) {
        return DiscordUtil.isAdmin(plugin, member);
    }

    public void stop() {
        shutdown = true;
        generation.incrementAndGet();
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    public void restart() {
        stop();
        startAsync();
    }
}

