package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShapeSessionInputResolverTest {
    @Test
    void 垂直直线只沿Y轴解析第二点并保留高度偏移() {
        ShapeBuildTypes.Session session = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                EnumFacing.UP,
                EnumFacing.UP,
                new BlockPos(10, 64, 10),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                0.0D);

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session,
                hit(new BlockPos(18, 70, 15)),
                false,
                true,
                false,
                0,
                0,
                null,
                null);

        assertEquals(new BlockPos(10, 70, 10), input.pointB());
        assertEquals(6, input.boxHeightOffset());
    }

    @Test
    void 第二点使用相机射线与水平形状平面() {
        ShapeBuildTypes.Session session = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                EnumFacing.NORTH,
                EnumFacing.UP,
                new BlockPos(10, 64, 10),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                0.0D);
        RayTraceResult fallback = new RayTraceResult(
                new Vec3d(14.0D, 63.0D, 13.0D),
                EnumFacing.UP,
                new BlockPos(14, 63, 13));

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session,
                fallback,
                false,
                true,
                0,
                0,
                new Vec3d(14.2D, 80.5D, 13.8D),
                new Vec3d(0.0D, -1.0D, 0.0D));

        assertEquals(new BlockPos(14, 64, 13), input.pointB());
        assertEquals(true, input.connectedLine());
        assertEquals(0, input.boxHeightOffset());
    }

    @Test
    void 平行或反向射线回退到鼠标命中位置() {
        ShapeBuildTypes.Session session = session(
                BuildShape.CIRCLE,
                EnumFacing.UP,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                null,
                0);
        RayTraceResult fallback = hit(new BlockPos(4, 65, 6));

        assertEquals(new BlockPos(4, 64, 6),
                ShapeSessionInputResolver.resolve(
                        session, fallback, false, false, 0, 0,
                        new Vec3d(0.0D, 70.0D, 0.0D),
                        new Vec3d(1.0D, 0.0D, 0.0D)).pointB());
        assertEquals(new BlockPos(4, 64, 6),
                ShapeSessionInputResolver.resolve(
                        session, fallback, false, false, 0, 0,
                        new Vec3d(0.0D, 60.0D, 0.0D),
                        new Vec3d(0.0D, -1.0D, 0.0D)).pointB());
    }

    @Test
    void 脚印微调在解析输入前沿形状平面轴应用() {
        ShapeBuildTypes.Session session = session(
                BuildShape.BOX,
                EnumFacing.NORTH,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                new BlockPos(12, 64, 13),
                5);

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session, null, true, false, 2, -1, null, null);

        assertEquals(new BlockPos(14, 64, 12), input.pointB());
        assertEquals(5, input.boxHeightOffset());
    }

    @Test
    void 未完成阶段和缺失关键点保持保守空结果() {
        ShapeBuildTypes.Session waitingThird = session(
                BuildShape.BOX,
                EnumFacing.UP,
                ShapeBuildTypes.Phase.NEED_THIRD_POINT,
                new BlockPos(2, 64, 2),
                0);
        assertNull(ShapeSessionInputResolver.resolve(
                waitingThird, null, true, false, 0, 0, null, null));

        ShapeBuildTypes.Session missingFirst = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                EnumFacing.UP,
                EnumFacing.UP,
                null,
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                0.0D);
        assertNull(ShapeSessionInputResolver.resolve(
                missingFirst, null, false, false, 0, 0, null, null));
    }

    private static ShapeBuildTypes.Session session(
            BuildShape shape,
            EnumFacing planeFace,
            ShapeBuildTypes.Phase phase,
            BlockPos pointB,
            int heightOffset) {
        return new ShapeBuildTypes.Session(
                shape,
                planeFace,
                EnumFacing.UP,
                new BlockPos(10, 64, 10),
                pointB,
                phase,
                heightOffset,
                0.0D);
    }

    private static RayTraceResult hit(BlockPos pos) {
        return new RayTraceResult(
                new Vec3d(pos).add(0.5D, 0.5D, 0.5D),
                EnumFacing.UP,
                pos);
    }
}
