package com.flavor.huhobot;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家头像：读取 Paper Profile 纹理，兼容 SkinRestorer；只在内存中临时缓存。 */
public final class AvatarService {

    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_CACHE = 512;

    public record Request(String name, String key, String skinUrl) {
    }

    private record Cached(long expiresAt, String dataUri) {
    }

    private final FlavorDiscordBridge plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public AvatarService(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    /** 必须在主线程调用。 */
    public Request request(OfflinePlayer player) {
        if (player == null) {
            return new Request("?", "unknown", "");
        }
        String name = player.getName() == null ? "?" : player.getName();
        String key = player.getUniqueId() == null ? name : player.getUniqueId().toString();
        String skin = "";
        try {
            var profile = player.getPlayerProfile();
            if (profile != null && profile.getTextures() != null
                    && profile.getTextures().getSkin() != null) {
                URL url = profile.getTextures().getSkin();
                if ("https".equalsIgnoreCase(url.getProtocol()) || "http".equalsIgnoreCase(url.getProtocol())) {
                    skin = url.toString();
                }
            }
        } catch (Exception ignored) {
            // 离线玩家或皮肤服务不可用时使用默认头像
        }
        return new Request(name, key, skin);
    }

    public Request request(Player player) {
        return request((OfflinePlayer) player);
    }

    /** 异步获取头像；不写磁盘。 */
    public CompletableFuture<Map<String, String>> resolveAll(List<Request> requests) {
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
        for (Request request : requests) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    Map.entry(request.key(), dataUri(request))));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, String> result = new ConcurrentHashMap<>();
                    for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                        try {
                            Map.Entry<String, String> entry = future.join();
                            result.put(entry.getKey(), entry.getValue());
                        } catch (Exception failure) {
                        }
                    }
                    return result;
                });
    }

    public String dataUri(Request request) {
        String steve = plugin.imageRenderer().steveHead();
        if (request == null || request.skinUrl().isBlank()) {
            return steve;
        }
        String cacheKey = request.key() + "|" + request.skinUrl();
        Cached cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.dataUri();
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(https(request.skinUrl())))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Flavor-HuHoBot-Discord-Bridge/1.0.3")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(httpRequest,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                byte[] head = cropHead(response.body());
                String dataUri = HtmlImageRenderer.pngDataUri(head);
                put(cacheKey, dataUri);
                return dataUri;
            }
        } catch (Exception ignored) {
            // 网络失败或皮肤格式异常时使用默认史蒂夫
        }
        return steve;
    }

    private void put(String key, String dataUri) {
        if (cache.size() > MAX_CACHE) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
            if (cache.size() > MAX_CACHE) {
                cache.clear();
            }
        }
        cache.put(key, new Cached(System.currentTimeMillis() + CACHE_MILLIS, dataUri));
    }

    private static String https(String url) {
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    public static byte[] cropHead(byte[] skinBytes) throws Exception {
        Image skin = ImageIO.read(new ByteArrayInputStream(skinBytes));
        if (skin == null) {
            throw new IllegalArgumentException("无效的皮肤图片");
        }
        BufferedImage head = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = head.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(skin, 0, 0, 64, 64, 8, 8, 16, 16, null);
            graphics.drawImage(skin, 0, 0, 64, 64, 40, 8, 48, 16, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(head, "png", output);
        return output.toByteArray();
    }
}
