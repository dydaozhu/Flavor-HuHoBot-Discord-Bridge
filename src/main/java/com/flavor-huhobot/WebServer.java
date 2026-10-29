package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 本机 WebUI：默认只监听 127.0.0.1，使用 Bearer Token 鉴权。 */
public final class WebServer {

    private static final Set<String> EDITABLE = Set.of(
            "discord.guild-id",
            "discord.channels.game-to-discord",
            "discord.channels.discord-to-game",
            "discord.channels.agent",
            "discord.channels.audit",
            "bridge.enabled",
            "bridge.from-game",
            "bridge.from-discord",
            "bridge.start-with",
            "bridge.link-required",
            "link.whitelist-on-link",
            "agent.enabled",
            "agent.base-url",
            "agent.api-key",
            "agent.model",
            "agent.mode",
            "agent.max-steps",
            "agent.daily-token-budget",
            "security.command-mode"
    );

    private final FlavorDiscordBridge plugin;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    private HttpServer server;
    private ExecutorService executor;
    private String tokenHash;

    public WebServer(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.config().webuiEnabled()) {
            return;
        }
        try {
            ensureToken();
            String bind = plugin.config().webuiBind();
            int port = plugin.config().webuiPort();
            if (!bind.equals("127.0.0.1") && !bind.equals("localhost")) {
                plugin.getLogger().warning("WebUI 绑定在 " + bind + "，管理接口可能暴露到网络，请务必配置防火墙。");
            }
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "FlavorWeb");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("WebUI 已启动: http://" + bind + ":" + port);
        } catch (Exception error) {
            plugin.getLogger().warning("WebUI 启动失败: " + error.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'");

            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                serve(exchange, "/web/index.html", "text/html; charset=utf-8");
                return;
            }
            if (method.equals("GET") && path.equals("/app.js")) {
                serve(exchange, "/web/app.js", "application/javascript; charset=utf-8");
                return;
            }
            if (method.equals("GET") && path.equals("/style.css")) {
                serve(exchange, "/web/style.css", "text/css; charset=utf-8");
                return;
            }
            if (method.equals("POST") && path.equals("/api/login")) {
                login(exchange);
                return;
            }
            if (!authorized(exchange)) {
                json(exchange, 401, error("未授权"));
                return;
            }
            switch (method + " " + path) {
                case "GET /api/status" -> json(exchange, 200, status());
                case "GET /api/config" -> json(exchange, 200, config());
                case "POST /api/config" -> saveConfig(exchange);
                case "GET /api/audit" -> json(exchange, 200, audit(exchange));
                case "POST /api/reload" -> reload(exchange);
                default -> json(exchange, 404, error("Not Found"));
            }
        } catch (Throwable error) {
            try {
                json(exchange, 500, error(error.getMessage() == null ? "internal error" : error.getMessage()));
            } catch (Exception ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------ API

    private void login(HttpExchange exchange) throws IOException {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (rateLimited(ip)) {
            json(exchange, 429, error("尝试过于频繁，请稍后再试"));
            return;
        }
        JsonNode body = readBody(exchange);
        String token = body == null ? "" : body.path("token").asText("");
        if (!verify(token)) {
            json(exchange, 401, error("令牌错误"));
            return;
        }
        json(exchange, 200, Json.obj().put("ok", true));
    }

    private ObjectNode status() {
        ObjectNode node = Json.obj();
        node.put("version", plugin.getDescription().getVersion());
        node.put("discord", plugin.discordBot().isReady());
        node.put("guild", plugin.discordBot().guild() == null ? "" : plugin.discordBot().guild().getName());
        node.put("online", Bukkit.getOnlinePlayers().size());
        node.put("links", plugin.links().size());
        node.put("agent", plugin.config().agentEnabled());
        node.put("mode", plugin.config().agentMode());
        node.put("pendingApprovals", plugin.agentService().approvals().size());
        String guildId = plugin.config().guildId();
        node.put("tokensToday", guildId.isBlank() ? 0 : plugin.usage().today(guildId));
        return node;
    }

    private ObjectNode config() {
        var raw = plugin.config().raw();
        ObjectNode root = Json.obj();
        root.put("language", plugin.config().language());

        ObjectNode discord = root.putObject("discord");
        discord.put("guild-id", raw.getString("discord.guild-id", ""));
        discord.put("token", raw.getString("discord.token", "").isBlank() ? "" : "••••••••");
        ObjectNode channels = discord.putObject("channels");
        channels.put("game-to-discord", raw.getString("discord.channels.game-to-discord", ""));
        channels.put("discord-to-game", raw.getString("discord.channels.discord-to-game", ""));
        channels.put("agent", raw.getString("discord.channels.agent", ""));
        channels.put("audit", raw.getString("discord.channels.audit", ""));

        ObjectNode bridge = root.putObject("bridge");
        bridge.put("enabled", plugin.config().bridgeEnabled());
        bridge.put("from-game", plugin.config().fromGame());
        bridge.put("from-discord", plugin.config().fromDiscord());
        bridge.put("start-with", plugin.config().startWith());
        bridge.put("link-required", plugin.config().linkRequiredForChat());

        ObjectNode link = root.putObject("link");
        link.put("whitelist-on-link", plugin.config().whitelistOnLink());

        ObjectNode agent = root.putObject("agent");
        agent.put("enabled", plugin.config().agentEnabled());
        agent.put("base-url", plugin.config().agentBaseUrl());
        agent.put("model", plugin.config().agentModel());
        agent.put("mode", plugin.config().agentMode());
        agent.put("max-steps", plugin.config().agentMaxSteps());
        agent.put("daily-token-budget", plugin.config().agentDailyTokenBudget());
        agent.put("api-key", plugin.config().agentApiKey().isBlank() ? "" : "••••••••");

        root.putObject("security").put("command-mode", plugin.config().commandMode());
        ObjectNode webui = root.putObject("webui");
        webui.put("bind", plugin.config().webuiBind());
        webui.put("port", plugin.config().webuiPort());
        return root;
    }

    private void saveConfig(HttpExchange exchange) throws IOException {
        JsonNode body = readBody(exchange);
        JsonNode changes = body == null ? null : body.path("changes");
        if (changes == null || !changes.isObject()) {
            json(exchange, 400, error("缺少 changes"));
            return;
        }
        int applied = 0;
        var iterator = changes.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String path = entry.getKey();
            if (!EDITABLE.contains(path)) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (path.endsWith("token") || path.endsWith("api-key")) {
                String text = value.asText("");
                if (text.isBlank() || text.contains("•")) {
                    continue;
                }
            }
            apply(path, value);
            applied++;
        }
        plugin.config().save();
        json(exchange, 200, Json.obj().put("ok", true).put("applied", applied));
    }

    private void apply(String path, JsonNode value) {
        if (value.isBoolean()) {
            plugin.config().set(path, value.asBoolean());
        } else if (value.isIntegralNumber()) {
            plugin.config().set(path, value.asLong());
        } else if (value.isArray()) {
            List<String> list = Json.MAPPER.convertValue(value,
                    Json.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            plugin.config().set(path, list);
        } else {
            plugin.config().set(path, value.asText());
        }
    }

    private ArrayNode audit(HttpExchange exchange) {
        int limit = 50;
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.startsWith("limit=")) {
            try {
                limit = Math.min(500, Math.max(1, Integer.parseInt(query.substring(6))));
            } catch (NumberFormatException ignored) {
            }
        }
        ArrayNode array = Json.arr();
        plugin.audit().recent(limit).forEach(array::add);
        return array;
    }

    private void reload(HttpExchange exchange) throws IOException {
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                plugin.reloadAll();
                return true;
            }).get(15, java.util.concurrent.TimeUnit.SECONDS);
            json(exchange, 200, Json.obj().put("ok", true));
        } catch (Exception error) {
            json(exchange, 500, error("重载失败: " + error.getMessage()));
        }
    }

    // ------------------------------------------------------------------ 鉴权

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        return verify(header.substring(7).trim());
    }

    private boolean verify(String token) {
        if (token == null || token.isBlank() || tokenHash == null) {
            return false;
        }
        byte[] actual = sha256(token).getBytes(StandardCharsets.UTF_8);
        byte[] expected = tokenHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private void ensureToken() throws IOException {
        File file = new File(plugin.getDataFolder(), "webui-token");
        if (file.isFile()) {
            String stored = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            if (!stored.isBlank()) {
                tokenHash = stored;
                return;
            }
        }
        String plain = randomToken(40);
        tokenHash = sha256(plain);
        Files.writeString(file.toPath(), tokenHash, StandardCharsets.UTF_8);
        plugin.getLogger().info("WebUI 访问令牌（仅首次生成，请妥善保存）: " + plain);
    }

    private boolean rateLimited(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > 60_000L) {
                deque.pollFirst();
            }
            if (deque.size() >= 10) {
                return true;
            }
            deque.addLast(now);
            return false;
        }
    }

    private String randomToken(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return null;
        }
        return Json.readOrNull(new String(bytes, StandardCharsets.UTF_8));
    }

    private void serve(HttpExchange exchange, String resource, String contentType) throws IOException {
        try (InputStream stream = WebServer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                json(exchange, 404, error("Not Found"));
                return;
            }
            byte[] bytes = stream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private void json(HttpExchange exchange, int code, JsonNode node) throws IOException {
        byte[] bytes = Json.write(node).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private ObjectNode error(String message) {
        return Json.obj().put("error", message == null ? "error" : message);
    }
}
