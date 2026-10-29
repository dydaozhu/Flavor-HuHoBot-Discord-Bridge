package com.flavor.huhobot;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 双向聊天桥接。 */
public final class ChatBridge {

    private final FlavorDiscordBridge plugin;

    public ChatBridge(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Discord -> MC

    public void onDiscordMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            return;
        }
        if (!plugin.config().bridgeEnabled()) {
            return;
        }
        String configuredGuild = plugin.config().guildId();
        if (!configuredGuild.isBlank() && !configuredGuild.equals(event.getGuild().getId())) {
            return;
        }
        String chatChannel = plugin.config().discordToGameChannelId();
        if (chatChannel.isBlank() || !chatChannel.equals(event.getChannel().getId())) {
            return;
        }
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        String selfId = plugin.discordBot().selfId();
        if (!selfId.isBlank() && selfId.equals(event.getAuthor().getId())) {
            return;
        }

        String content = event.getMessage().getContentDisplay().trim();
        StringBuilder attachments = new StringBuilder();
        for (Message.Attachment attachment : event.getMessage().getAttachments()) {
            attachments.append(attachment.isImage() ? " [图片]" : " [附件]");
        }
        if (content.isEmpty() && attachments.length() == 0) {
            return;
        }

        LinkStore.Link link = plugin.links().byDiscord(event.getAuthor().getId());
        if (plugin.config().linkRequiredForChat() && link == null) {
            return;
        }

        String displayName = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getName();
        String message = Text.sanitize(content) + attachments;
        String formatted = plugin.config().fromDiscord()
                .replace("{prefix}", "")
                .replace("{name}", Text.sanitize(displayName))
                .replace("{message}", message)
                .replace("{discord}", Text.sanitize(displayName))
                .replace("{mc}", link == null ? "" : Text.sanitize(link.mcName()));

        broadcast(formatted);
    }

    private void broadcast(String legacyText) {
        // Discord -> 游戏的聊天转发保持自己的模板，不加插件前缀
        Component component = Text.component(legacyText);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getConsoleSender().sendMessage(component);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        });
    }

    // ------------------------------------------------------------------ MC -> Discord

    public void onMcChat(AsyncChatEvent event) {
        if (!plugin.config().bridgeEnabled()) {
            return;
        }
        String chatChannel = plugin.config().gameToDiscordChannelId();
        if (chatChannel.isBlank()) {
            return;
        }
        String message = Text.plain(event.message()).trim();
        if (message.isEmpty()) {
            return;
        }
        String startWith = plugin.config().startWith();
        if (!startWith.isBlank()) {
            if (!message.startsWith(startWith)) {
                return;
            }
            message = message.substring(startWith.length()).trim();
            if (message.isEmpty()) {
                return;
            }
        }
        String playerName = event.getPlayer().getName();
        String formatted = plugin.config().fromGame()
                .replace("{prefix}", "")
                .replace("{name}", playerName)
                .replace("{player}", playerName)
                .replace("{message}", message);
        plugin.discordBot().sender().sendText(chatChannel, formatted);
    }

    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.config().bridgeEnabled() || !plugin.config().joinEnabled()) {
            return;
        }
        String chatChannel = plugin.config().gameToDiscordChannelId();
        if (chatChannel.isBlank()) {
            return;
        }
        plugin.discordBot().sender().sendText(chatChannel,
                plugin.config().joinFormat().replace("{prefix}", "")
                        .replace("{name}", event.getPlayer().getName())
                        .replace("{player}", event.getPlayer().getName()));
    }

    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.config().bridgeEnabled() || !plugin.config().quitEnabled()) {
            return;
        }
        String chatChannel = plugin.config().gameToDiscordChannelId();
        if (chatChannel.isBlank()) {
            return;
        }
        plugin.discordBot().sender().sendText(chatChannel,
                plugin.config().quitFormat().replace("{prefix}", "")
                        .replace("{name}", event.getPlayer().getName())
                        .replace("{player}", event.getPlayer().getName()));
    }
}
