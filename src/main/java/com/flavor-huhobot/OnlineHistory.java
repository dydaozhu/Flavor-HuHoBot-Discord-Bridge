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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 在线人数采样历史，默认保留 1 天。 */
public final class OnlineHistory {

    public static final long SAMPLE_INTERVAL_MILLIS = 5 * 60 * 1000L;
    public static final long RETENTION_MILLIS = 24 * 60 * 60 * 1000L;

    public record Sample(long time, int count) {
    }

    private final File file;
    private final Deque<Sample> samples = new ArrayDeque<>();

    public OnlineHistory(File file) {
        this.file = file;
    }

    public synchronized void load() {
        samples.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            JsonNode root = Json.read(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isArray()) {
                return;
            }
            long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
            for (JsonNode node : root) {
                long time = node.path("time").asLong(0);
                if (time >= cutoff) {
                    samples.addLast(new Sample(time, Math.max(0, node.path("count").asInt(0))));
                }
            }
        } catch (Exception ignored) {
            // 损坏的历史文件不应阻止插件启动
        }
    }

    public synchronized void record(int count) {
        long now = System.currentTimeMillis();
        samples.addLast(new Sample(now, Math.max(0, count)));
        prune(now);
        save();
    }

    public synchronized List<Sample> snapshot() {
        prune(System.currentTimeMillis());
        return new ArrayList<>(samples);
    }

    public synchronized void save() {
        ArrayNode array = Json.arr();
        for (Sample sample : samples) {
            ObjectNode node = array.addObject();
            node.put("time", sample.time());
            node.put("count", sample.count());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
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

    private void prune(long now) {
        long cutoff = now - RETENTION_MILLIS;
        while (!samples.isEmpty() && samples.peekFirst().time() < cutoff) {
            samples.removeFirst();
        }
    }
}
