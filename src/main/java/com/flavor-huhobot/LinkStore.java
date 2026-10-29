package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Discord 用户与 Minecraft 账号的绑定关系，落盘为 binds.json。 */
public final class LinkStore {

    public record Link(String discordId, String mcUuid, String mcName, long linkedAt) {
    }

    private final File file;
    private final Map<String, Link> byDiscord = new ConcurrentHashMap<>();
    private final Map<String, Link> byUuid = new ConcurrentHashMap<>();
    private final Map<String, Link> byName = new ConcurrentHashMap<>();

    public LinkStore(File file) {
        this.file = file;
    }

    public synchronized void load() {
        byDiscord.clear();
        byUuid.clear();
        byName.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            JsonNode root = Json.read(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isArray()) {
                return;
            }
            for (JsonNode node : root) {
                Link link = new Link(
                        node.path("discordId").asText(),
                        node.path("mcUuid").asText(),
                        node.path("mcName").asText(),
                        node.path("linkedAt").asLong()
                );
                if (!link.discordId().isBlank() && !link.mcName().isBlank()) {
                    index(link);
                }
            }
        } catch (Exception ignored) {
            // 损坏的数据文件不应阻止插件启动
        }
    }

    public synchronized void save() {
        ArrayNode array = Json.arr();
        for (Link link : byDiscord.values()) {
            ObjectNode node = array.addObject();
            node.put("discordId", link.discordId());
            node.put("mcUuid", link.mcUuid());
            node.put("mcName", link.mcName());
            node.put("linkedAt", link.linkedAt());
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try {
            File temp = new File(parent, file.getName() + ".tmp");
            Files.writeString(temp.toPath(), Json.pretty(array), StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void put(Link link) {
        removeByDiscord(link.discordId());
        removeByUuid(link.mcUuid());
        removeByName(link.mcName());
        index(link);
        save();
    }

    public synchronized boolean removeByDiscord(String discordId) {
        Link removed = byDiscord.remove(discordId);
        if (removed == null) {
            return false;
        }
        byUuid.remove(removed.mcUuid());
        byName.remove(key(removed.mcName()));
        save();
        return true;
    }

    public synchronized boolean removeByUuid(String uuid) {
        for (Link link : byDiscord.values()) {
            if (link.mcUuid().equals(uuid)) {
                return removeByDiscord(link.discordId());
            }
        }
        return false;
    }

    private void removeByUuid(String uuid, boolean save) {
        Link removed = byUuid.remove(uuid);
        if (removed != null) {
            byDiscord.remove(removed.discordId());
            byName.remove(key(removed.mcName()));
        }
    }

    private void removeByName(String name) {
        Link removed = byName.remove(key(name));
        if (removed != null) {
            byDiscord.remove(removed.discordId());
            byUuid.remove(removed.mcUuid());
        }
    }

    public Link byDiscord(String discordId) {
        return discordId == null ? null : byDiscord.get(discordId);
    }

    public Link byUuid(String uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public Link byName(String name) {
        return name == null ? null : byName.get(key(name));
    }

    public Collection<Link> all() {
        return byDiscord.values();
    }

    public int size() {
        return byDiscord.size();
    }

    private void index(Link link) {
        byDiscord.put(link.discordId(), link);
        if (!link.mcUuid().isBlank()) {
            byUuid.put(link.mcUuid(), link);
        }
        byName.put(key(link.mcName()), link);
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
