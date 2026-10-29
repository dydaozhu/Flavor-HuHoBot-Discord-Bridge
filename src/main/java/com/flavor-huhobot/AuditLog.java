package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 追加式审计日志（JSONL），用于记录 AI 与管理员的关键操作。 */
public final class AuditLog {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final File file;
    private final Object lock = new Object();

    public AuditLog(File file) {
        this.file = file;
    }

    public void record(String actorId, String actorName, String action, String detail, String result, String approvalId) {
        ObjectNode node = Json.obj();
        node.put("ts", System.currentTimeMillis());
        node.put("time", Instant.now().toString());
        node.put("actorId", safe(actorId));
        node.put("actorName", safe(actorName));
        node.put("action", safe(action));
        node.put("detail", safe(detail));
        node.put("result", safe(result));
        node.put("approvalId", safe(approvalId));

        String line = Json.write(node) + System.lineSeparator();
        synchronized (lock) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return;
                }
                rotateIfNeeded();
                Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }

    public List<JsonNode> recent(int limit) {
        List<JsonNode> result = new ArrayList<>();
        synchronized (lock) {
            if (!file.isFile()) {
                return result;
            }
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                int from = Math.max(0, lines.size() - Math.max(1, limit));
                for (int i = from; i < lines.size(); i++) {
                    JsonNode node = Json.readOrNull(lines.get(i));
                    if (node != null) {
                        result.add(node);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        Collections.reverse(result);
        return result;
    }

    private void rotateIfNeeded() throws IOException {
        if (file.isFile() && file.length() > MAX_BYTES) {
            Files.move(file.toPath(), new File(file.getParentFile(), file.getName() + ".1").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }
}
