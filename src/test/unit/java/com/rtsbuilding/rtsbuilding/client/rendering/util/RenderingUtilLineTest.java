package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RenderingUtilLineTest {
    @Test
    void forgeLineVertexFillsPositionColorAndNormal() {
        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        RenderingUtil.line(
                buffer,
                new PoseStack(),
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                1.0F, 0.5F, 0.25F, 1.0F);

        assertDoesNotThrow(() -> buffer.end().release());
    }
}
