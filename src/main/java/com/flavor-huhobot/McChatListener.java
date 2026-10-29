package com.flavor.huhobot;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Bukkit 事件监听。 */
public final class McChatListener implements Listener {

    private final FlavorDiscordBridge plugin;

    public McChatListener(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        plugin.chatBridge().onMcChat(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.forceBindService().handleJoin(event.getPlayer())) {
            return;
        }
        plugin.chatBridge().onJoin(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.chatBridge().onQuit(event);
    }
}
