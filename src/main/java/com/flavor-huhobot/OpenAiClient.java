package com.flavor.huhobot;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** OpenAI Chat Completions 兼容客户端（支持 tools/function calling）。 */
public final class OpenAiClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;

    public OpenAiClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ChatResult chat(List<com.fasterxml.jackson.databind.node.ObjectNode> messages,
                           com.fasterxml.jackson.databind.node.ArrayNode tools) throws IOException, InterruptedException {
        com.fasterxml.jackson.databind.node.ObjectNode body = Json.obj();
        body.put("model", model);
        body.set("messages", Json.MAPPER.valueToTree(messages));
        body.set("tools", tools);
        body.put("tool_choice", "auto");
        body.put("temperature", temperature);
        body.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI 接口 HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return parse(response.body());
    }

    private ChatResult parse(String body) throws IOException {
        com.fasterxml.jackson.databind.JsonNode root = Json.read(body);
        com.fasterxml.jackson.databind.JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new IOException("AI 响应缺少 choices");
        }
        com.fasterxml.jackson.databind.JsonNode message = choices.get(0).path("message");
        String content = message.hasNonNull("content") ? message.get("content").asText() : null;

        List<ToolCall> calls = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            int index = 0;
            for (com.fasterxml.jackson.databind.JsonNode call : toolCalls) {
                String id = call.path("id").asText("call_" + index++);
                String name = call.path("function").path("name").asText("");
                String arguments = call.path("function").path("arguments").asText("{}");
                if (!name.isBlank()) {
                    calls.add(new ToolCall(id, name, arguments));
                }
            }
        }
        long tokens = root.path("usage").path("total_tokens").asLong(0L);
        return new ChatResult(content, calls, tokens);
    }

    private String endpoint() {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "…" : value;
    }
}
