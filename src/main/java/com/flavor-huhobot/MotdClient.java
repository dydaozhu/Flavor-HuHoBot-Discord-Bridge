package com.flavor.huhobot;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** motd.txssb.cn 查询客户端。 */
public final class MotdClient {

    private static final String ENDPOINT = "https://motd.minebbs.com/api/status";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Result query(String rawAddress) throws IOException, InterruptedException {
        String address = rawAddress == null ? "" : rawAddress.trim();
        while (address.startsWith("/")) {
            address = address.substring(1).trim();
        }
        if (address.startsWith("http://")) {
            address = address.substring(7);
        } else if (address.startsWith("https://")) {
            address = address.substring(8);
        }
        int slash = address.indexOf('/');
        if (slash >= 0) {
            address = address.substring(0, slash).trim();
        }
        if (address.isEmpty()) {
            throw new IOException("地址为空");
        }

        String host = address;
        String port = "";
        int colon = address.lastIndexOf(':');
        if (colon > 0 && colon < address.length() - 1) {
            String maybePort = address.substring(colon + 1);
            if (maybePort.matches("\\d{1,5}")) {
                host = address.substring(0, colon);
                port = maybePort;
            }
        }
        if (!host.matches("[A-Za-z0-9.-]{1,253}")) {
            throw new IOException("地址格式不正确");
        }

        String url = ENDPOINT + "?ip=" + URLEncoder.encode(host, StandardCharsets.UTF_8)
                + "&srv=true";
        if (!port.isBlank()) {
            url += "&port=" + URLEncoder.encode(port, StandardCharsets.UTF_8);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "Flavor-HuHoBot-Discord-Bridge/1.0.4")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MOTD 接口 HTTP " + response.statusCode());
        }

        JsonNode root = Json.read(response.body());
        boolean online = "online".equalsIgnoreCase(root.path("status").asText());
        String motd = root.path("motd").asText("");
        if (motd.isBlank()) {
            motd = root.path("pureMotd").asText("");
        }
        String version = root.path("version").asText("");
        int onlinePlayers = root.path("players").path("online").asInt(0);
        int maxPlayers = root.path("players").path("max").asInt(0);
        long delay = root.path("delay").asLong(0);
        String error = root.path("error").asText("");
        String queried = root.path("resolved").asText(root.path("host").asText(address));
        String icon = root.path("icon").asText("");
        String motdComponent = root.path("motd").isContainerNode()
                ? root.path("motd").toString()
                : "";
        return new Result(queried, online, motd, version, onlinePlayers, maxPlayers,
                delay, error, icon, motdComponent);
    }

    public record Result(
            String address,
            boolean online,
            String motd,
            String version,
            int onlinePlayers,
            int maxPlayers,
            long delay,
            String error,
            String icon,
            String motdComponent
    ) {
    }
}
