package com.flavor.huhobot;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;

/** 统一的 Discord 发送入口：自动分片、自动记录错误、后续可替换为队列。 */
public final class MessageSender {

    private static final int MAX_CHARS = 1900;

    private final FlavorDiscordBridge plugin;

    public MessageSender(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    public void sendText(String channelId, String text) {
        if (channelId == null || channelId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            MessageChannel channel = plugin.discordBot().messageChannel(channelId);
            if (channel == null) {
                plugin.getLogger().warning("找不到 Discord 频道: " + channelId);
                return;
            }
            String clean = Text.stripColorCodes(text);
            for (String part : Text.split(clean, MAX_CHARS)) {
                if (part.isBlank()) {
                    continue;
                }
                channel.sendMessage(part).queue(null,
                        error -> plugin.getLogger().warning("发送 Discord 消息失败: " + error.getMessage()));
            }
        } catch (Exception error) {
            plugin.getLogger().warning("发送 Discord 消息失败: " + error.getMessage());
        }
    }

    public void sendEmbed(String channelId, MessageEmbed embed, ActionRow... rows) {
        if (channelId == null || channelId.isBlank() || embed == null) {
            return;
        }
        try {
            MessageChannel channel = plugin.discordBot().messageChannel(channelId);
            if (channel == null) {
                plugin.getLogger().warning("找不到 Discord 频道: " + channelId);
                return;
            }
            var action = channel.sendMessageEmbeds(embed);
            if (rows != null && rows.length > 0) {
                action.setComponents(rows);
            }
            action.queue(null, error -> plugin.getLogger().warning("发送 Discord Embed 失败: " + error.getMessage()));
        } catch (Exception error) {
            plugin.getLogger().warning("发送 Discord Embed 失败: " + error.getMessage());
        }
    }

    public void editEmbed(String channelId, String messageId, MessageEmbed embed) {
        if (channelId == null || messageId == null || embed == null) {
            return;
        }
        try {
            MessageChannel channel = plugin.discordBot().messageChannel(channelId);
            if (channel == null) {
                return;
            }
            channel.editMessageEmbedsById(messageId, embed)
                    .setComponents()
                    .queue(null, error -> plugin.getLogger().warning("编辑 Discord Embed 失败: " + error.getMessage()));
        } catch (Exception error) {
            plugin.getLogger().warning("编辑 Discord Embed 失败: " + error.getMessage());
        }
    }
}
