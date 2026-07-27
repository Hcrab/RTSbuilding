package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 只访问官方 HTTPS relay 的 Java 8 SSE 客户端。
 *
 * <p>请求仍只发送 question 字段，不携带供应商密钥。连接禁止重定向，保留连接/读取超时；取消
 * Future 时会主动断开底层连接，关闭或刷新聊天窗口不会留下持续占用的网络读取。</p>
 */
public final class RtsAiChatClient {
    public static final URI ENDPOINT = URI.create("https://rts-ai.wordmate.site/v1/chat");

    public CompletableFuture<Void> ask(final String prompt,
                                       final Consumer<String> onChunk,
                                       final Consumer<String> onError,
                                       final Runnable onComplete) {
        final AtomicReference<HttpsURLConnection> activeConnection = new AtomicReference<HttpsURLConnection>();
        final CompletableFuture<Void> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    connection = (HttpsURLConnection) ENDPOINT.toURL().openConnection();
                    activeConnection.set(connection);
                    connection.setConnectTimeout(8_000);
                    connection.setReadTimeout(45_000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setRequestProperty("Accept", "text/event-stream");
                    connection.setDoOutput(true);

                    JsonObject requestJson = new JsonObject();
                    requestJson.addProperty("question", prompt);
                    byte[] body = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(body.length);
                    OutputStream output = connection.getOutputStream();
                    try {
                        output.write(body);
                    } finally {
                        output.close();
                    }

                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) {
                        InputStream error = connection.getErrorStream();
                        String detail = error == null ? "" : readAll(error);
                        onError.accept("HTTP " + status + ": " + compact(detail));
                        return;
                    }
                    readSse(connection.getInputStream(), onChunk);
                    if (!Thread.currentThread().isInterrupted()) {
                        onComplete.run();
                    }
                } catch (IOException failure) {
                    if (!Thread.currentThread().isInterrupted()) {
                        onError.accept(compact(failure.getMessage()));
                    }
                } catch (RuntimeException failure) {
                    if (!Thread.currentThread().isInterrupted()) {
                        onError.accept(compact(failure.getMessage()));
                    }
                } finally {
                    activeConnection.compareAndSet(connection, null);
                    if (connection != null) connection.disconnect();
                }
            }
        });
        future.whenComplete((ignored, failure) -> {
            if (future.isCancelled()) {
                HttpsURLConnection connection = activeConnection.getAndSet(null);
                if (connection != null) connection.disconnect();
            }
        });
        return future;
    }

    static void readSse(InputStream stream, Consumer<String> onChunk) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonObject root = new JsonParser().parse(data).getAsJsonObject();
                if (!root.has("choices") || root.getAsJsonArray("choices").size() == 0) continue;
                JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (!choice.has("delta")) continue;
                JsonObject delta = choice.getAsJsonObject("delta");
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    onChunk.accept(delta.get("content").getAsString());
                }
            }
        } finally {
            reader.close();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown error";
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240) + "...";
    }
}
