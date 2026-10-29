package com.flavor.huhobot;


import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 控制台命令安全策略。
 *
 * <p>默认黑名单模式：拦截 blocked-commands，其余命令允许执行。
 * allowlist 模式只允许 allowed-commands 中的命令。
 * 同时兼容 minecraft: 命名空间，并对 /execute 这类包装命令做全 token 扫描，
 * 防止 execute ... run op 绕过。
 */
public final class CommandPolicy {

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() {
            return new Decision(true, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    private final String mode;
    private final Set<String> allowed;
    private final List<String> blocked;
    private final int maxLength;

    public CommandPolicy(ConfigManager config) {
        this(config.commandMode(), config.allowedCommands(), config.blockedCommands(), config.maxCommandLength());
    }

    public CommandPolicy(String mode, Collection<String> allowed, Collection<String> blocked, int maxLength) {
        this.mode = mode == null ? "blacklist" : mode.trim().toLowerCase(Locale.ROOT);
        this.allowed = new HashSet<>();
        if (allowed != null) {
            for (String value : allowed) {
                if (value != null && !value.isBlank()) {
                    this.allowed.add(stripNamespace(value.trim().toLowerCase(Locale.ROOT)));
                }
            }
        }
        this.blocked = blocked == null ? List.of() : blocked.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> stripNamespace(value.trim().toLowerCase(Locale.ROOT)))
                .toList();
        this.maxLength = Math.max(32, maxLength);
    }

    public Decision check(String rawCommand) {
        if (rawCommand == null) {
            return Decision.deny("命令为空");
        }
        String command = rawCommand.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return Decision.deny("命令为空");
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            return Decision.deny("命令包含换行");
        }
        if (command.length() > maxLength) {
            return Decision.deny("命令过长");
        }

        String normalized = command.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split(" ");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return Decision.deny("命令为空");
        }

        // 兼容 minecraft:give / minecraft:op 这类带命名空间的写法
        tokens[0] = stripNamespace(tokens[0]);
        String policyCommand = String.join(" ", tokens);
        String first = tokens[0];

        for (String entry : blocked) {
            if (policyCommand.equals(entry) || policyCommand.startsWith(entry + " ")) {
                return Decision.deny("命中黑名单: " + entry);
            }
        }

        // 只对包装类命令做全 token 扫描，避免玩家名/聊天内容里的普通单词误伤
        if (first.equals("execute")) {
            for (String token : tokens) {
                if (blocked.contains(stripNamespace(token))) {
                    return Decision.deny("命中黑名单: " + token);
                }
            }
        }

        if ("allowlist".equals(mode) && !allowed.contains(first)) {
            return Decision.deny("命令不在允许列表: " + first);
        }
        return Decision.allow();
    }

    private static String stripNamespace(String value) {
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }
}
