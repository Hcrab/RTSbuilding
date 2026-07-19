package com.rtsbuilding.rtsbuilding.client.screen.handler;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenCursorPickerTest {
    @Test
    void inverseProjectionKeepsScreenRightOnWorldRight() {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(90.0D), 2.0F, 0.05F, 1000.0F);

        Vec3 center = ScreenCursorPicker.unprojectRayDirection(projection, 0.0D, 0.0D);
        Vec3 right = ScreenCursorPicker.unprojectRayDirection(projection, 0.75D, 0.0D);

        assertEquals(0.0D, center.x, 1.0E-5D);
        assertEquals(0.0D, center.y, 1.0E-5D);
        assertTrue(center.z < 0.0D, "投影中心应沿相机前方射出");
        assertTrue(right.x > 0.0D, "屏幕右侧不能再被镜像到世界左侧");
        assertTrue(right.z < 0.0D);
    }
}
