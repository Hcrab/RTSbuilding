package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildGhostBlockStateResolverTest {
    @Test
    void placementYawUsesTheSameCursorRayConventionAsTheServer() {
        assertEquals(0.0F, BuildGhostBlockStateResolver.placementYawFromRay(new Vec3(0.0D, 0.0D, 1.0D)));
        assertEquals(90.0F, BuildGhostBlockStateResolver.placementYawFromRay(new Vec3(-1.0D, 0.0D, 0.0D)));
        assertEquals(-90.0F, BuildGhostBlockStateResolver.placementYawFromRay(new Vec3(1.0D, 0.0D, 0.0D)));
    }
}
