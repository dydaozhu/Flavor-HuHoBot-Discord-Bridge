package com.flavor.huhobot;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 把命令输出收集成文本，用于 Discord 的 /whitelist list。 */
final class CommandOutput {

    private CommandOutput() {
    }

    static String run(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return "";
        }
        CapturingSender sender = new CapturingSender();
        org.bukkit.Bukkit.dispatchCommand(sender, command);
        return String.join("\n", sender.lines).trim();
    }

    private static final class CapturingSender implements CommandSender {
        private final CommandSender delegate = org.bukkit.Bukkit.getConsoleSender();
        private final List<String> lines = new ArrayList<>();

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.isBlank()) {
                lines.add(message);
            }
        }

        @Override
        public void sendMessage(String... messages) {
            if (messages != null) {
                for (String message : messages) {
                    sendMessage(message);
                }
            }
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            sendMessage(message);
        }

        @Override
        public void sendMessage(UUID sender, String... messages) {
            sendMessage(messages);
        }

        @Override
        public void sendMessage(Component message) {
            sendMessage(PlainTextComponentSerializer.plainText().serialize(message));
        }

        @Override
        public void sendMessage(Identity source, Component message, MessageType type) {
            sendMessage(message);
        }

        @Override
        public Server getServer() {
            return delegate.getServer();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public CommandSender.Spigot spigot() {
            return delegate.spigot();
        }

        @Override
        public Component name() {
            return delegate.name();
        }

        @Override
        public boolean isPermissionSet(String name) {
            return delegate.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return delegate.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(String name) {
            return delegate.hasPermission(name);
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return delegate.hasPermission(perm);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return delegate.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return delegate.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return delegate.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return delegate.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            delegate.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            delegate.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return delegate.getEffectivePermissions();
        }

        @Override
        public boolean isOp() {
            return delegate.isOp();
        }

        @Override
        public void setOp(boolean value) {
            delegate.setOp(value);
        }
    }
}
