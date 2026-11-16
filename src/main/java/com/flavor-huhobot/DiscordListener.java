package com.flavor.huhobot;

import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

/** Discord 普通消息入口，交给 ChatBridge 处理。 */
public final class DiscordListener extends ListenerAdapter {

    private final FlavorDiscordBridge plugin;

    public DiscordListener(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        plugin.chatBridge().onDiscordMessage(event);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String configuredGuild = plugin.config().guildId();
        if (!configuredGuild.isBlank() && !configuredGuild.equals(event.getGuild().getId())) {
            return;
        }
        String discordId = event.getUser().getId();
        if (discordId.equals(plugin.discordBot().selfId())) {
            return;
        }
        if (plugin.links().byDiscordAll(discordId).isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.linkService().unlinkByDiscord(discordId, true)) {
                plugin.getLogger().info("Discord 用户 " + event.getUser().getName()
                        + " 退出 Discord 服务器，已自动解除绑定"
                        + (plugin.config().whitelistOnLink() ? "并移除白名单" : ""));
            }
        });
    }
}

