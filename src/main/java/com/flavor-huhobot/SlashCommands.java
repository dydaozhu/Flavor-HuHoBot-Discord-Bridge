package com.flavor.huhobot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 所有 Slash 命令。 */
public final class SlashCommands extends ListenerAdapter {

    private final FlavorDiscordBridge plugin;

    public SlashCommands(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    public static List<CommandData> definitions(FlavorDiscordBridge plugin) {
        Messages i18n = plugin.messages();
        List<CommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("agent", i18n.discord("command.agent.description")).addSubcommands(
                new SubcommandData("run", i18n.discord("command.agent.run"))
                        .addOption(OptionType.STRING, "task", i18n.discord("command.agent.task"), true),
                new SubcommandData("stop", i18n.discord("command.agent.stop")),
                new SubcommandData("new", i18n.discord("command.agent.new"))
        ));
        commands.add(Commands.slash("bind", i18n.discord("command.bind.description"))
                .addOption(OptionType.STRING, "value", i18n.discord("command.bind.value"), true));
        commands.add(Commands.slash("unbind", i18n.discord("command.unbind.description")));
        commands.add(Commands.slash("status", i18n.discord("command.status.description")));
        commands.add(Commands.slash("online", i18n.discord("command.online.description")));
        commands.add(Commands.slash("whitelist", i18n.discord("command.whitelist.description")).addSubcommands(
                new SubcommandData("add", i18n.discord("command.whitelist.add"))
                        .addOption(OptionType.STRING, "player", i18n.discord("command.whitelist.player"), true),
                new SubcommandData("remove", i18n.discord("command.whitelist.remove"))
                        .addOption(OptionType.STRING, "player", i18n.discord("command.whitelist.player"), true),
                new SubcommandData("list", i18n.discord("command.whitelist.list"))
        ));
        commands.add(Commands.slash("console", i18n.discord("command.console.description"))
                .addOption(OptionType.STRING, "command", i18n.discord("command.console.command"), true));
        commands.add(Commands.slash("help", i18n.discord("command.help.description")));
        commands.add(Commands.slash("reload", i18n.discord("command.reload.description")));
        return commands;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("此命令只能在服务器频道内使用。").setEphemeral(true).queue();
            return;
        }
        String configured = plugin.config().guildId();
        if (!configured.isBlank() && !configured.equals(event.getGuild().getId())) {
            event.reply("此服务器未授权使用该机器人。").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "agent" -> handleAgent(event);
            case "bind" -> handleBind(event);
            case "unbind" -> handleUnbind(event);
            case "status" -> handleStatus(event);
            case "online" -> handleOnline(event);
            case "whitelist" -> handleWhitelist(event);
            case "console" -> handleConsole(event);
            case "help" -> handleHelp(event);
            case "reload" -> handleReload(event);
            default -> event.reply(plugin.messages().discord("unknown-command")).setEphemeral(true).queue();
        }
    }

    // ------------------------------------------------------------------ agent

    private void handleAgent(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) {
            return;
        }
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply(plugin.messages().discord("unknown-command")).setEphemeral(true).queue();
            return;
        }
        switch (sub) {
            case "run" -> {
                OptionMapping taskOption = event.getOption("task");
                String task = taskOption == null ? "" : taskOption.getAsString();
                plugin.agentService().start(event, task);
            }
            case "stop" -> {
                plugin.agentService().stop(event.getGuild().getId(), event.getUser().getId());
                event.reply(plugin.messages().discord("agent-stopped")).setEphemeral(true).queue();
            }
            case "new" -> {
                boolean cleared = plugin.agentService().clearContext(
                        event.getGuild().getId(), event.getUser().getId());
                event.reply(plugin.messages().discord(cleared ? "agent-context-cleared" : "agent-context-busy"))
                        .setEphemeral(true).queue();
            }
            default -> event.reply(plugin.messages().discord("unknown-command")).setEphemeral(true).queue();
        }
    }

    // ------------------------------------------------------------------ link

    private void handleBind(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        LinkStore.Link existing = plugin.links().byDiscord(discordId);
        if (existing != null) {
            event.reply("你已经绑定了角色 **" + existing.mcName() + "**。如需更换，请先执行 /unbind。")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping valueOption = event.getOption("value");
        String value = valueOption == null ? "" : valueOption.getAsString().trim();
        String discordName = event.getMember() == null
                ? event.getUser().getName()
                : event.getMember().getEffectiveName();

        if (plugin.config().bindingForceBind()) {
            if (!value.matches("\\d{5}")) {
                event.reply("请输入游戏内拿到的 5 位验证码。").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    ForceBindService.Result result = plugin.forceBindService().consume(value, discordId);
                    if (result.status() != ForceBindService.Status.OK) {
                        String key = switch (result.status()) {
                            case EXPIRED -> "bind-expired";
                            case RATE_LIMITED -> "bind-rate-limited";
                            default -> "bind-invalid";
                        };
                        event.getHook().sendMessage(plugin.messages().discord(key)).setEphemeral(true).queue();
                        return;
                    }
                    LinkService.Result link = plugin.linkService().completeLink(
                            discordId,
                            discordName,
                            result.pending().playerUuid(),
                            result.pending().playerName(),
                            "force-bind"
                    );
                    String message = switch (link.status()) {
                        case OK -> plugin.messages().discord("bind-success",
                                Map.of("player", result.pending().playerName()));
                        case DISCORD_TAKEN -> plugin.messages().discord("link-discord-taken",
                                Map.of("player", result.pending().playerName()));
                        case PLAYER_TAKEN -> plugin.messages().discord("link-player-taken");
                    };
                    event.getHook().sendMessage(message).setEphemeral(true).queue();
                } catch (Throwable error) {
                    plugin.getLogger().warning("强制绑定失败: " + error.getMessage());
                    event.getHook().sendMessage("绑定失败：" + error.getMessage()).setEphemeral(true).queue();
                }
            });
            return;
        }

        if (!value.matches("[A-Za-z0-9_]{1,16}")) {
            event.reply("游戏 ID 不合法（1-16 位字母、数字或下划线）。").setEphemeral(true).queue();
            return;
        }
        String playerName = value;
        event.deferReply(true).queue();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                LinkService.Result result = plugin.linkService().linkDirect(discordId, discordName, playerName);
                String message = switch (result.status()) {
                    case OK -> plugin.messages().discord("link-success",
                            Map.of("player", result.link() == null ? playerName : result.link().mcName()));
                    case DISCORD_TAKEN -> plugin.messages().discord("link-discord-taken",
                            Map.of("player", result.link() == null ? "?" : result.link().mcName()));
                    case PLAYER_TAKEN -> plugin.messages().discord("link-player-taken");
                };
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } catch (Throwable error) {
                plugin.getLogger().warning("直接绑定失败: " + error.getMessage());
                event.getHook().sendMessage("绑定失败：" + error.getMessage()).setEphemeral(true).queue();
            }
        });
    }

    private void handleUnbind(SlashCommandInteractionEvent event) {
        boolean removed = plugin.linkService().unlinkByDiscord(event.getUser().getId(), true);
        event.reply(plugin.messages().discord(removed ? "link-removed" : "link-none"))
                .setEphemeral(true).queue();
    }

    // ------------------------------------------------------------------ status

    private void handleStatus(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        EmbedBuilder embed = DiscordUtil.base(plugin.messages().discord("status-title"))
                .addField("服务器",
                        Bukkit.getServer().getName() + " " + Bukkit.getServer().getBukkitVersion(), true)
                .addField("Discord",
                        plugin.discordBot().isReady() ? plugin.discordBot().jda().getSelfUser().getName() : "未连接", true)
                .addField("AI Agent",
                        plugin.config().agentEnabled()
                                ? "开启 (" + plugin.config().agentMode() + ")"
                                : "关闭", true)
                .addField("在线玩家", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
                .addField("已绑定账号", String.valueOf(plugin.links().size()), true)
                .addField("今日 token", String.valueOf(plugin.usage().today(guild.getId())), true)
                .setFooter("Flavor-HuHoBot-Discord-Bridge v" + plugin.getDescription().getVersion());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleOnline(SlashCommandInteractionEvent event) {
        List<String> names = Bukkit.getOnlinePlayers().stream()
                .map(player -> player.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String value = names.isEmpty() ? "当前没有玩家在线。" : String.join(", ", names);
        MessageEmbed embed = DiscordUtil.base("在线玩家 (" + names.size() + ")")
                .setDescription(value.length() > 3900 ? value.substring(0, 3900) + "…" : value)
                .build();
        event.replyEmbeds(embed).queue();
    }

    // ------------------------------------------------------------------ whitelist

    private void handleWhitelist(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) {
            return;
        }
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply(plugin.messages().discord("unknown-command")).setEphemeral(true).queue();
            return;
        }
        if ("list".equals(sub)) {
            event.deferReply(true).queue();
            Bukkit.getScheduler().runTask(plugin, () -> {
                String value;
                String listCommand = plugin.config().whitelistListCommand();
                if (listCommand != null && !listCommand.isBlank()) {
                    try {
                        value = Text.stripColorCodes(CommandOutput.run(listCommand));
                    } catch (Throwable error) {
                        plugin.getLogger().warning("执行白名单查询命令失败: " + error.getMessage());
                        value = "";
                    }
                } else {
                    value = "";
                }
                if (value.isBlank()) {
                    List<String> names = Bukkit.getWhitelistedPlayers().stream()
                            .map(OfflinePlayer::getName)
                            .filter(Objects::nonNull)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    value = names.isEmpty()
                            ? plugin.messages().discord("whitelist-empty")
                            : String.join(", ", names);
                }
                event.getHook().sendMessage(value.length() > 1900 ? value.substring(0, 1900) + "…" : value)
                        .setEphemeral(true).queue();
            });
            return;
        }

        OptionMapping playerOption = event.getOption("player");
        String player = playerOption == null ? "" : playerOption.getAsString().trim();
        if (player.isEmpty() || !player.matches("[A-Za-z0-9_]{1,16}")) {
            event.reply("玩家名不合法。").setEphemeral(true).queue();
            return;
        }
        boolean add = "add".equals(sub);
        event.deferReply(true).queue();
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "whitelist " + (add ? "add " : "remove ") + player);
            plugin.audit().record(event.getUser().getId(), event.getUser().getName(),
                    add ? "whitelist-add" : "whitelist-remove", player, String.valueOf(ok), "");
            event.getHook().sendMessage(ok
                            ? plugin.messages().discord(add ? "whitelist-added" : "whitelist-removed", Map.of("player", player))
                            : "命令执行失败，请确认服务端已开启白名单。")
                    .setEphemeral(true).queue();
        });
    }

    // ------------------------------------------------------------------ console

    private void handleConsole(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) {
            return;
        }
        OptionMapping commandOption = event.getOption("command");
        String command = commandOption == null ? "" : commandOption.getAsString().trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            event.reply("命令不能为空。").setEphemeral(true).queue();
            return;
        }
        CommandPolicy.Decision decision = plugin.commandPolicy().check(command);
        if (!decision.allowed()) {
            event.reply(plugin.messages().discord("console-denied", Map.of("reason", decision.reason())))
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        String finalCommand = command;
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            plugin.audit().record(event.getUser().getId(), event.getUser().getName(),
                    "console", finalCommand, String.valueOf(ok), "");
            event.getHook().sendMessage(plugin.messages().discord("console-executed", Map.of("command", finalCommand))
                            + (ok ? "" : "（返回 false）"))
                    .setEphemeral(true).queue();
        });
    }

    // ------------------------------------------------------------------ help / flavor

    private void handleHelp(SlashCommandInteractionEvent event) {
        MessageEmbed embed = DiscordUtil.base(plugin.messages().discord("help-title"))
                .setDescription("""
                        `/agent run <task>` - 让 AI 执行服务器管理任务（管理员）
                        `/agent new` - 清除当前 AI 上下文
                        `/agent stop` - 停止当前任务
                        `/bind <游戏ID或验证码>` - 未开强制绑定填游戏ID；开启后填5位验证码
                        `/unbind` - 解除绑定
                        `/status` - 服务器与机器人状态
                        `/online` - 在线玩家
                        `/whitelist add|remove|list` - 白名单管理（管理员）
                        `/console <command>` - 执行控制台命令（管理员）
                        `/reload` - 重载所有文件并重连机器人（管理员）
                        """)
                .setFooter("Flavor-HuHoBot-Discord-Bridge v" + plugin.getDescription().getVersion())
                .build();
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void handleReload(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) {
            return;
        }
        event.reply(plugin.messages().discord("reloaded")).setEphemeral(true).queue();
        Bukkit.getScheduler().runTask(plugin, plugin::reloadAll);
    }

    // ------------------------------------------------------------------ helpers

    private boolean requireAdmin(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !plugin.discordBot().isAdmin(member)) {
            event.reply(plugin.messages().discord("no-permission")).setEphemeral(true).queue();
            return false;
        }
        return true;
    }
}
