package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 只访问官方 HTTPS relay 的轻量 SSE 客户端。
 *
 * <p>API 密钥和模型回退均留在边缘函数中；模组只发送一个 {@code question} 字段，
 * 不携带任何供应商密钥，也不会尝试 HTTP 降级。
 */
public final class RtsAiChatClient {
    public static final URI ENDPOINT = URI.create("https://rts-ai.wordmate.site/v1/chat");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public CompletableFuture<Void> ask(String prompt,
                                       Consumer<String> onChunk,
                                       Consumer<String> onError,
                                       Runnable onComplete) {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("question", prompt);
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString(), StandardCharsets.UTF_8))
                .build();

        return CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<InputStream> response = this.httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String detail = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    onError.accept("HTTP " + response.statusCode() + ": " + compact(detail));
                    return;
                }
                readSse(response.body(), onChunk);
                onComplete.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException failure) {
                onError.accept(compact(failure.getMessage()));
            }
        });
    }

    static void readSse(InputStream stream, Consumer<String> onChunk) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).strip();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                if (!root.has("choices") || root.getAsJsonArray("choices").isEmpty()) {
                    continue;
                }
                JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (!choice.has("delta")) {
                    continue;
                }
                JsonObject delta = choice.getAsJsonObject("delta");
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    onChunk.accept(delta.get("content").getAsString());
                }
            }
        }
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) {
            return "unknown error";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').strip();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240) + "...";
    }
}
