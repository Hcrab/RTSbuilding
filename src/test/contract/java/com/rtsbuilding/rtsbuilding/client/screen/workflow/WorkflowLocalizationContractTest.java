package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowLocalizationContractTest {

    private static final Path LANG_ROOT = Path.of("src/main/resources/assets/rtsbuilding/lang");
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "zh_tw", "zh_hk");

    @Test
    void allMaintainedLanguagesExposeTheSameTranslationKeys() throws IOException {
        Set<String> englishKeys = readLanguage("en_us").keySet();
        for (String language : LANGUAGES) {
            assertEquals(englishKeys, readLanguage(language).keySet(),
                    language + " must expose exactly the same translation keys as en_us");
        }
        assertTrue(englishKeys.contains("screen.rtsbuilding.workflow.type.mine_single"));
        assertTrue(englishKeys.contains("screen.rtsbuilding.workflow.type.place_single"));
        assertTrue(englishKeys.contains("screen.rtsbuilding.workflow.resume_placement.title"));
        assertTrue(englishKeys.contains("screen.rtsbuilding.workflow.blueprint_resume.title"));
        assertTrue(englishKeys.contains("message.rtsbuilding.gui_binding.open_failed"));
    }

    @Test
    void workflowAndBindingUiDoNotRestoreChinesePlayerFacingLiterals() throws IOException {
        List<Path> sources = List.of(
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsWorkflowPanel.java"),
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsResumePlacementPanel.java"),
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsBlueprintResumePanel.java"),
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsInteractionHandlers.java"),
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/core/RtsWorkflowEngine.java"),
                Path.of("src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/model/RtsWorkflowStatus.java"));

        List<String> forbidden = List.of(
                "Component.literal(\"\u6062\u590d\u653e\u7f6e\")",
                "Component.literal(\"\u84dd\u56fe\u6750\u6599\u6e05\u5355\")",
                "-> \"\u6316\u6398\"",
                "-> \"\u8fde\u9501\u6316\u6398\"",
                "-> \"\u533a\u57df\u6316\u6398\"",
                "\"\u7269\u54c1\u4e0d\u8db3\"",
                "\"\u5df2\u6682\u505c\"");

        for (Path source : sources) {
            String code = Files.readString(source);
            for (String literal : forbidden) {
                assertFalse(code.contains(literal),
                        source + " must not restore a player-facing Chinese literal: " + literal);
            }
        }
    }

    private static JsonObject readLanguage(String language) throws IOException {
        return JsonParser.parseString(Files.readString(LANG_ROOT.resolve(language + ".json"))).getAsJsonObject();
    }
}
