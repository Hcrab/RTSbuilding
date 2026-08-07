package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定已迁出的偏好与工作流投影不得重新回流到客户端总控制器。 */
class ClientRtsControllerOwnershipContractTest {
    private static final Path ROOT = Path.of(
            "src/client/java/com/rtsbuilding/rtsbuilding/client/controller");

    @Test
    void preferenceAndWorkflowOwnersStayFocusedAndInheritedByController() throws Exception {
        String controller = source("ClientRtsController.java");
        String preferences = source("ClientRtsPreferenceFacade.java");
        String workflows = source("ClientRtsWorkflowFacade.java");

        assertTrue(controller.contains("extends ClientRtsWorkflowFacade"));
        assertTrue(workflows.contains("extends ClientRtsPreferenceFacade"));
        assertLineLimit("ClientRtsPreferenceFacade.java", 500);
        assertLineLimit("ClientRtsWorkflowFacade.java", 500);
        assertLineLimit("ClientRtsStateQueryOwner.java", 500);
        assertLineLimit("ClientRtsLifecycleOwner.java", 500);
        assertLineLimit("ClientRtsCommandOwner.java", 500);
        assertLineLimit("ClientRtsInteractionOwner.java", 500);
        assertMethodLimit("ClientRtsController.java", 250);

        assertFalse(controller.contains("private boolean damageSoundEnabled"));
        assertFalse(controller.contains("public void applyWorkflowProgress("));
        assertTrue(preferences.contains("protected final CameraOrbitService cameraOrbitService"));
        assertTrue(workflows.contains("protected final ClientWorkflowStateManager workflowStateManager"));
    }

    @Test
    void controllerDelegatesConcreteSessionWorkInsteadOfReabsorbingIt() throws Exception {
        String controller = source("ClientRtsController.java");

        assertTrue(controller.contains("this.lifecycleOwner.tick()"));
        assertTrue(controller.contains("this.commandOwner.requestStoragePage(page)"));
        assertTrue(controller.contains("this.interactionOwner.placeSelected("));
        assertTrue(controller.contains("this.stateQueryOwner.getStorageEntries()"));

        assertFalse(controller.contains("minecraft.setScreen(new BuilderScreen("));
        assertFalse(controller.contains("RtsClientPacketGateway.sendLinkStorage("));
        assertFalse(controller.contains("this.buildPlacementService.placeSelected("));
        assertFalse(controller.contains("this.miningOperationService.confirmAreaMine("));
    }

    private static String source(String name) throws Exception {
        return Files.readString(ROOT.resolve(name), StandardCharsets.UTF_8);
    }

    private static void assertLineLimit(String name, int maximum) throws Exception {
        long lines;
        try (var source = Files.lines(ROOT.resolve(name), StandardCharsets.UTF_8)) {
            lines = source.count();
        }
        assertTrue(lines <= maximum, name + " 超过 owner 门禁：" + lines + " > " + maximum);
    }

    private static void assertMethodLimit(String name, int maximum) throws Exception {
        String source = source(name);
        long methods = java.util.regex.Pattern.compile(
                        "(?m)^\\s*(?:public|protected|private)\\s+(?:static\\s+)?(?:final\\s+)?"
                                + "(?:<[^>]+>\\s+)?[\\w<>?,.\\[\\]]+\\s+\\w+\\s*\\(")
                .matcher(source)
                .results()
                .count();
        assertTrue(methods <= maximum, name + " 超过职责方法门禁：" + methods + " > " + maximum);
    }
}
