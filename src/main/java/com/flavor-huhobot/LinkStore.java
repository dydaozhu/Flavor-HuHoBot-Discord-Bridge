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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Discord 用户与 Minecraft 账号的绑定关系，落盘为 binds.json。 */
public final class LinkStore {

    public record Link(String discordId, String mcUuid, String mcName, long linkedAt, boolean locked) {
        public Link(String discordId, String mcUuid, String mcName, long linkedAt) {
            this(discordId, mcUuid, mcName, linkedAt, false);
        }
    }

    private final File file;
    private final Map<String, Link> byUuid = new ConcurrentHashMap<>();
    private final Map<String, Link> byName = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Link>> byDiscord = new ConcurrentHashMap<>();

    public LinkStore(File file) {
        this.file = file;
    }

    public synchronized void load() {
        byUuid.clear();
        byName.clear();
        byDiscord.clear();
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
                        node.path("linkedAt").asLong(),
                        node.path("locked").asBoolean(false)
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
        for (Link link : all()) {
            ObjectNode node = array.addObject();
            node.put("discordId", link.discordId());
            node.put("mcUuid", link.mcUuid());
            node.put("mcName", link.mcName());
            node.put("linkedAt", link.linkedAt());
            node.put("locked", link.locked());
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
        Link oldUuid = byUuid.get(link.mcUuid());
        if (oldUuid != null) {
            removeFromDiscord(oldUuid);
        }
        Link oldName = byName.get(key(link.mcName()));
        if (oldName != null) {
            removeFromDiscord(oldName);
        }
        byUuid.remove(link.mcUuid());
        byName.remove(key(link.mcName()));

        byDiscord.computeIfAbsent(link.discordId(), ignored -> new ConcurrentHashMap<>())
                .put(linkKey(link), link);
        index(link);
        save();
    }

    /** 移除该 Discord 用户的全部绑定。 */
    public synchronized boolean removeByDiscord(String discordId) {
        Map<String, Link> links = byDiscord.remove(discordId);
        if (links == null || links.isEmpty()) {
            return false;
        }
        for (Link link : links.values()) {
            byUuid.remove(link.mcUuid());
            byName.remove(key(link.mcName()));
        }
        save();
        return true;
    }

    /** 移除指定 Discord 用户的一个游戏账号。 */
    public synchronized boolean removeLink(String discordId, String mcUuidOrName) {
        Map<String, Link> links = byDiscord.get(discordId);
        if (links == null) {
            return false;
        }
        Link target = null;
        for (Link link : links.values()) {
            if (link.mcUuid().equalsIgnoreCase(mcUuidOrName)
                    || link.mcName().equalsIgnoreCase(mcUuidOrName)) {
                target = link;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        links.remove(linkKey(target));
        if (links.isEmpty()) {
            byDiscord.remove(discordId);
        }
        byUuid.remove(target.mcUuid());
        byName.remove(key(target.mcName()));
        save();
        return true;
    }

    public synchronized boolean removeByUuid(String uuid) {
        Link link = byUuid.get(uuid);
        return link != null && removeLink(link.discordId(), link.mcUuid());
    }

    public synchronized boolean setLocked(String discordId, String mcUuid, boolean locked) {
        Map<String, Link> links = byDiscord.get(discordId);
        if (links == null) {
            return false;
        }
        Link link = links.get(mcUuid);
        if (link == null) {
            for (Link candidate : links.values()) {
                if (candidate.mcUuid().equalsIgnoreCase(mcUuid)
                        || candidate.mcName().equalsIgnoreCase(mcUuid)) {
                    link = candidate;
                    break;
                }
            }
        }
        if (link == null) {
            return false;
        }
        Link updated = new Link(link.discordId(), link.mcUuid(), link.mcName(), link.linkedAt(), locked);
        links.put(linkKey(link), updated);
        index(updated);
        save();
        return true;
    }

    /** 兼容旧调用：返回该 Discord 用户最早绑定的账号。 */
    public Link byDiscord(String discordId) {
        List<Link> links = byDiscordAll(discordId);
        return links.stream().min(Comparator.comparingLong(Link::linkedAt)).orElse(null);
    }

    public List<Link> byDiscordAll(String discordId) {
        if (discordId == null) {
            return List.of();
        }
        Map<String, Link> links = byDiscord.get(discordId);
        return links == null ? List.of() : links.values().stream()
                .sorted(Comparator.comparingLong(Link::linkedAt))
                .toList();
    }

    public int countByDiscord(String discordId) {
        Map<String, Link> links = byDiscord.get(discordId);
        return links == null ? 0 : links.size();
    }

    public Link byUuid(String uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public Link byName(String name) {
        return name == null ? null : byName.get(key(name));
    }

    public Collection<Link> all() {
        List<Link> links = new ArrayList<>();
        for (Map<String, Link> entry : byDiscord.values()) {
            links.addAll(entry.values());
        }
        return links;
    }

    public int size() {
        int total = 0;
        for (Map<String, Link> entry : byDiscord.values()) {
            total += entry.size();
        }
        return total;
    }

    private void index(Link link) {
        byDiscord.computeIfAbsent(link.discordId(), ignored -> new ConcurrentHashMap<>())
                .put(linkKey(link), link);
        if (!link.mcUuid().isBlank()) {
            byUuid.put(link.mcUuid(), link);
        }
        byName.put(key(link.mcName()), link);
    }

    private void removeFromDiscord(Link link) {
        Map<String, Link> links = byDiscord.get(link.discordId());
        if (links == null) {
            return;
        }
        links.remove(linkKey(link));
        if (links.isEmpty()) {
            byDiscord.remove(link.discordId());
        }
    }

    private static String linkKey(Link link) {
        return link.mcUuid().isBlank() ? key(link.mcName()) : link.mcUuid();
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
