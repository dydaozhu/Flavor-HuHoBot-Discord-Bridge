package com.flavor.huhobot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
}
