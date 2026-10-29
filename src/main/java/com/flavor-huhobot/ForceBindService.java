package com.flavor.huhobot;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 强制绑定：未绑定玩家进服时被踢出并拿到 5 位验证码，
 * 在 Discord 执行 /bind &lt;验证码&gt; 完成绑定后重新进服。
 */
public final class ForceBindService {

    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    public enum Status {
        OK,
        INVALID,
        EXPIRED,
        RATE_LIMITED
    }

    public record Pending(String code, String playerName, String playerUuid, long expiresAt) {
    }

    public record Result(Status status, Pending pending) {
    }

    private final FlavorDiscordBridge plugin;
    private final Map<String, Pending> byCode = new ConcurrentHashMap<>();
    private final Map<String, Pending> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public ForceBindService(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家进服时调用。
     *
     * @return true 表示允许继续进服；false 表示已被踢出
     */
    public boolean handleJoin(Player player) {
        if (!plugin.config().bindingForceBind()) {
            return true;
        }
        if (player.hasPermission("flavor-huhobot.bypass") || player.hasPermission("flavor-huhobot.admin")) {
            return true;
        }
        if (isExempt(player.getName())) {
            return true;
        }
        if (plugin.links().byUuid(player.getUniqueId().toString()) != null
                || plugin.links().byName(player.getName()) != null) {
            return true;
        }

        String code = issue(player);
        String groups = plugin.config().bindingForceBindGroups().isEmpty()
                ? "未配置"
                : String.join(", ", plugin.config().bindingForceBindGroups());
        player.kick(Text.component(plugin.messages().getPrefixed("force-bind-kick",
                Map.of("code", code, "groups", groups))));
        plugin.audit().record(player.getUniqueId().toString(), player.getName(),
                "force-bind-issue", player.getName(), "kicked", "");
        plugin.getLogger().info("强制绑定：已踢出未绑定玩家 " + player.getName() + "，并签发验证码。");
        return false;
    }

    public Result consume(String rawCode, String discordId) {
        if (rateLimited(discordId)) {
            return new Result(Status.RATE_LIMITED, null);
        }
        if (rawCode == null || !rawCode.trim().matches("\\d{5}")) {
            return new Result(Status.INVALID, null);
        }
        String code = rawCode.trim();
        Pending pending = byCode.get(code);
        if (pending == null) {
            return new Result(Status.INVALID, null);
        }
        if (System.currentTimeMillis() > pending.expiresAt()) {
            remove(pending);
            return new Result(Status.EXPIRED, null);
        }
        if (!byCode.remove(code, pending)) {
            return new Result(Status.INVALID, null);
        }
        byPlayer.remove(key(pending), pending);
        return new Result(Status.OK, pending);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        for (Pending pending : byCode.values()) {
            if (now > pending.expiresAt()) {
                remove(pending);
            }
        }
    }

    private String issue(Player player) {
        Pending old = byPlayer.remove(key(player.getName(), player.getUniqueId().toString()));
        if (old != null) {
            byCode.remove(old.code(), old);
        }
        Pending pending;
        do {
            String code = String.format("%05d", random.nextInt(100_000));
            pending = new Pending(code, player.getName(), player.getUniqueId().toString(),
                    System.currentTimeMillis() + CODE_TTL_MILLIS);
        } while (byCode.putIfAbsent(pending.code(), pending) != null);
        byPlayer.put(key(pending), pending);
        return pending.code();
    }

    private boolean isExempt(String playerName) {
        for (String exempt : plugin.config().bindingVerifyExempt()) {
            if (exempt.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    private boolean rateLimited(String discordId) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = attempts.computeIfAbsent(discordId, key -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > 60_000L) {
                queue.pollFirst();
            }
            if (queue.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                return true;
            }
            queue.addLast(now);
            return false;
        }
    }

    private void remove(Pending pending) {
        byCode.remove(pending.code(), pending);
        byPlayer.remove(key(pending), pending);
    }

    private String key(Pending pending) {
        return key(pending.playerName(), pending.playerUuid());
    }

    private String key(String playerName, String playerUuid) {
        return playerName.toLowerCase(Locale.ROOT) + "|" + playerUuid;
    }
}
