package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsAiChatClientTest {
    @Test
    void relayEndpointIsHttpsOnly() {
        assertEquals("https", RtsAiChatClient.ENDPOINT.getScheme());
        assertEquals("rts-ai.wordmate.site", RtsAiChatClient.ENDPOINT.getHost());
    }

    @Test
    void parsesOpenAiCompatibleSseChunks() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"role":"assistant","content":"RTS"}}]}

                data: {"choices":[{"delta":{"content":"Building"}}]}

                data: [DONE]
                """;
        List<String> chunks = new ArrayList<>();

        RtsAiChatClient.readSse(new ByteArrayInputStream(
                sse.getBytes(StandardCharsets.UTF_8)), chunks::add);

        assertEquals(List.of("RTS", "Building"), chunks);
    }
}
