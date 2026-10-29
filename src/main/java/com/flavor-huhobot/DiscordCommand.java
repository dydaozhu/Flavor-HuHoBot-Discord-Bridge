package com.flavor.huhobot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

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
        String text = plugin.messages().prefix() + "&r帮助\n"
                + clickable("/fh unbind", "解除绑定") + "\n"
                + clickable("/fh status", "查看状态") + "\n"
                + clickable("/fh reload", "重载所有文件并重连机器人（管理员）") + "\n"
                + clickable("/fh help", "查看帮助");
        sender.sendMessage(Text.component(text));
    }

    /** MiniMessage：点击命令文本会把命令放入聊天输入栏，而不是直接执行。 */
    private static String clickable(String command, String description) {
        return "<click:suggest_command:'" + command + "'>"
                + "<hover:show_text:'点击填入命令'>"
                + "<yellow>" + command + "</yellow>"
                + "</hover></click> <gray>- " + description + "</gray>";
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
