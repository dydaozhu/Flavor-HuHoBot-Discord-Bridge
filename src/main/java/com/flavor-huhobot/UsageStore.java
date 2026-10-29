package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 每个 Discord 服务器的每日 token 用量。 */
public final class UsageStore {

    private record Day(long epochDay, long tokens) {
    }

    private final File file;
    private final Map<String, Day> usage = new ConcurrentHashMap<>();

    public UsageStore(File file) {
        this.file = file;
    }

    public synchronized void load() {
        usage.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            JsonNode root = Json.read(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isObject()) {
                return;
            }
            root.fields().forEachRemaining(entry -> {
                JsonNode node = entry.getValue();
                usage.put(entry.getKey(), new Day(node.path("day").asLong(), node.path("tokens").asLong()));
            });
        } catch (Exception ignored) {
        }
    }

    public synchronized void add(String guildId, long tokens) {
        if (guildId == null || guildId.isBlank() || tokens <= 0) {
            return;
        }
        long today = LocalDate.now().toEpochDay();
        usage.compute(guildId, (key, old) -> old == null || old.epochDay() != today
                ? new Day(today, tokens)
                : new Day(today, old.tokens() + tokens));
        save();
    }

    public long today(String guildId) {
        Day day = usage.get(guildId);
        if (day == null || day.epochDay() != LocalDate.now().toEpochDay()) {
            return 0L;
        }
        return day.tokens();
    }

    public synchronized void save() {
        ObjectNode root = Json.obj();
        usage.forEach((guildId, day) -> {
            ObjectNode node = root.putObject(guildId);
            node.put("day", day.epochDay());
            node.put("tokens", day.tokens());
        });
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try {
            File temp = new File(parent, file.getName() + ".tmp");
            Files.writeString(temp.toPath(), Json.pretty(root), StandardCharsets.UTF_8);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
