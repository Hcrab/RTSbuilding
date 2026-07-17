package com.rtsbuilding.rtsbuilding.network.blueprint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止蓝图放置链路再次丢失可用于幂等接纳的稳定提交身份。 */
class BlueprintSubmissionIdentityContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void payloadTransportsSubmissionIdInBothCodecDirections() throws IOException {
        String payload = read("network/blueprint/C2SBlueprintPlacePayload.java");

        assertTrue(payload.contains("UUID submissionId"));
        assertTrue(payload.contains("buf.writeUUID(payload.submissionId())"));
        assertTrue(payload.contains("buf.readUUID()"));
    }

    @Test
    void oneExplicitClientSubmissionCreatesOneIdentityAndSendsThatPayload() throws IOException {
        String panel = read("client/screen/blueprint/BlueprintPanel.java");

        assertTrue(panel.contains("C2SBlueprintPlacePayload payload = new C2SBlueprintPlacePayload("));
        assertTrue(panel.contains("UUID.randomUUID()"));
        assertTrue(panel.contains("PacketDistributor.sendToServer(payload)"));
    }

    @Test
    void serverCarriesSubmissionIdIntoStronglyTypedBlueprintContext() throws IOException {
        String handler = read("network/blueprint/BlueprintNetworkHandlers.java");
        String context = read("server/pipeline/context/BlueprintContext.java");

        assertTrue(handler.contains(".submissionId(payload.submissionId())"));
        assertTrue(context.contains("TypedKey<UUID> ARG_SUBMISSION_ID"));
        assertTrue(context.contains("public UUID getSubmissionId()"));
        assertTrue(context.contains("public Builder submissionId(UUID submissionId)"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
