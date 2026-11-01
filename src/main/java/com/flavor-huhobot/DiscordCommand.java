package com.flavor.huhobot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 游戏内 /flavorhuhobot 命令（别名 /fh、/discord）。 */
public final class DiscordCommand implements CommandExecutor, TabCompleter {

    private final FlavorDiscordBridge plugin;

    public DiscordCommand(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "unbind" -> unbind(sender);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "help" -> help(sender);
            default -> plugin.messages().send(sender, "unknown-command");
        }
        return true;
    }

    private void unbind(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        boolean removed = plugin.linkService().unlinkByPlayer(player, true);
        plugin.messages().send(sender, removed ? "link-removed" : "link-none");
    }

    private void status(CommandSender sender) {
        if (!sender.hasPermission("flavor-huhobot.status")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        LinkStore.Link link = null;
        if (sender instanceof Player player) {
            link = plugin.links().byUuid(player.getUniqueId().toString());
            if (link == null) {
                link = plugin.links().byName(player.getName());
            }
        }
        String linkText = link == null ? "未绑定" : link.mcName() + " (Discord " + link.discordId() + ")";
        String text = plugin.messages().prefix() + "§r状态"
                + "\n§7Discord: " + (plugin.discordBot().isReady() ? "§a已连接" : "§c未连接")
                + "\n§7AI Agent: " + (plugin.config().agentEnabled() ? "§a开启 (" + plugin.config().agentMode() + ")" : "§c关闭")
                + "\n§7在线玩家: §f" + plugin.getServer().getOnlinePlayers().size()
                + "\n§7绑定: §f" + linkText;
        sender.sendMessage(Text.component(text));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("flavor-huhobot.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "reloaded");
    }

    private void help(CommandSender sender) {
        String text = plugin.messages().prefix() + "&r" + plugin.messages().get("help-header") + "\n"
                + line("help-unbind") + "\n"
                + line("help-status") + "\n"
                + line("help-reload") + "\n"
                + line("help-help");
        sender.sendMessage(Text.component(text));
    }

    /** 从语言文件生成可点击的命令行；点击只填入输入栏，不执行。 */
    private String line(String key) {
        return plugin.messages().get("help-line-format", Map.of(
                "command", plugin.messages().get(key + "-command"),
                "description", plugin.messages().get(key + "-description"),
                "hover", plugin.messages().get("help-hover")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("unbind", "status", "reload", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
