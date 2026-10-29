package com.flavor.huhobot;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 账号绑定（普通模式）。
 *
 * <p>未开启强制绑定时，Discord 执行 /bind &lt;游戏ID&gt; 直接写入绑定并加白名单。
 * 强制绑定模式由 {@link ForceBindService} 处理，不走这里。
 */
public final class LinkService {

    public enum Status {
        OK,
        PLAYER_TAKEN,
        DISCORD_TAKEN
    }

    public record Result(Status status, LinkStore.Link link) {
    }

    private final FlavorDiscordBridge plugin;

    public LinkService(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 直接绑定。
     *
     * <p>必须在主线程调用，因为 Bukkit 的玩家查询接口不是线程安全的。
     */
    public Result linkDirect(String discordId, String discordName, String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        String canonicalName = online == null ? playerName : online.getName();
        UUID uuid;
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            uuid = offline.getUniqueId();
            if (offline.getName() != null && !offline.getName().isBlank()) {
                canonicalName = offline.getName();
            }
        }
        return completeLink(discordId, discordName, uuid.toString(), canonicalName, "direct");
    }

    /**
     * 写入绑定并执行白名单操作。调用方负责保证在主线程执行。
     */
    public Result completeLink(String discordId, String discordName, String mcUuid, String mcName, String source) {
        LinkStore.Link byDiscord = plugin.links().byDiscord(discordId);
        if (byDiscord != null) {
            return new Result(Status.DISCORD_TAKEN, byDiscord);
        }
        LinkStore.Link byUuid = plugin.links().byUuid(mcUuid);
        LinkStore.Link byName = plugin.links().byName(mcName);
        if (byUuid != null || byName != null) {
            return new Result(Status.PLAYER_TAKEN, byUuid != null ? byUuid : byName);
        }

        LinkStore.Link link = new LinkStore.Link(discordId, mcUuid, mcName, System.currentTimeMillis());
        plugin.links().put(link);

        if (plugin.config().whitelistOnLink()) {
            dispatch(plugin.config().whitelistAddCommand().replace("{player}", mcName));
        }
        plugin.audit().record(discordId, discordName, "link:" + source, mcName, "ok", "");
        return new Result(Status.OK, link);
    }

    public boolean unlinkByDiscord(String discordId, boolean removeWhitelist) {
        LinkStore.Link link = plugin.links().byDiscord(discordId);
        if (link == null) {
            return false;
        }
        plugin.links().removeByDiscord(discordId);
        if (removeWhitelist && plugin.config().whitelistOnLink()) {
            dispatch(plugin.config().whitelistRemoveCommand().replace("{player}", link.mcName()));
        }
        plugin.audit().record(discordId, "", "unbind", link.mcName(), "ok", "");
        return true;
    }

    /** 游戏内玩家解绑：优先按 UUID，找不到时按玩家名（兼容离线 UUID 绑定）。 */
    public boolean unlinkByPlayer(Player player, boolean removeWhitelist) {
        LinkStore.Link link = plugin.links().byUuid(player.getUniqueId().toString());
        if (link == null) {
            link = plugin.links().byName(player.getName());
        }
        if (link == null) {
            return false;
        }
        return unlinkByDiscord(link.discordId(), removeWhitelist);
    }

    private void dispatch(String command) {
        Runnable task = () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
