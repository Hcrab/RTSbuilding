package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;

import java.util.List;

/**
 * Blueprint ghost preview data.
 * <p>
 * Used to render the preview blocks of a blueprint placement in-world.
 * Contains the preview block list, whether materials are ready, and whether
 * the preview was truncated.
 */
public record BlueprintGhostPreview(List<BlueprintPanel.BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
    /** Empty preview constant */
    public static final BlueprintGhostPreview EMPTY = new BlueprintGhostPreview(List.of(), false, false);
}
