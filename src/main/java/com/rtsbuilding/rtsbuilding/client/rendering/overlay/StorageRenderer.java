package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders linked storage outlines in the world. Normal storage links keep the
 * blue outline; extract-only links use pink so players can identify source-only
 * containers without opening the detail window.
 */
public final class StorageRenderer {
    private StorageRenderer() {
    }

    public static void renderLinkedStorages(Minecraft minecraft, ClientRtsController controller, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        if (minecraft.level == null || controller.getLinkedStorageEntries().isEmpty()) {
            return;
        }

        for (ClientRtsController.LinkedStorageEntry entry : controller.getLinkedStorageEntries()) {
            BlockPos pos = entry.pos();
            if (pos == null || !minecraft.level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            boolean extractOnly = entry.mode() == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
            float red = extractOnly ? 1.00F : 0.24F;
            float green = extractOnly ? 0.30F : 0.55F;
            float blue = extractOnly ? 0.82F : 1.00F;
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() - 0.002D,
                    pos.getY() - 0.002D,
                    pos.getZ() - 0.002D,
                    pos.getX() + 1.002D,
                    pos.getY() + 1.002D,
                    pos.getZ() + 1.002D,
                    red, green, blue, 1.0F);
        }
    }
}
