package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@org.junit.jupiter.api.extension.ExtendWith(com.rtsbuilding.rtsbuilding.test.ShapeConfigFixture.class)
class ShapeSessionInputResolverTest {
    @Test
    void 垂直直线只沿Y轴解析第二点并保留高度偏移() {
        ShapeBuildTypes.Session session = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                Direction.UP,
                Direction.UP,
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
                null,
                32,
                32);

        assertEquals(new BlockPos(10, 70, 10), input.pointB());
        assertEquals(6, input.boxHeightOffset());
    }

    @Test
    void 第二点使用相机射线与水平形状平面() {
        ShapeBuildTypes.Session session = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                Direction.NORTH,
                Direction.UP,
                new BlockPos(10, 64, 10),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                0.0D);
        BlockHitResult fallback = new BlockHitResult(
                new Vec3(14.0D, 63.0D, 13.0D),
                Direction.UP,
                new BlockPos(14, 63, 13),
                false);

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session,
                fallback,
                false,
                false,
                true,
                0,
                0,
                new Vec3(14.2D, 80.5D, 13.8D),
                new Vec3(0.0D, -1.0D, 0.0D),
                32,
                32);

        assertEquals(new BlockPos(14, 64, 13), input.pointB());
        assertEquals(true, input.connectedLine());
        assertEquals(0, input.boxHeightOffset());
    }

    @Test
    void 平行或反向射线回退到鼠标命中位置() {
        ShapeBuildTypes.Session session = session(
                BuildShape.CIRCLE,
                Direction.UP,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                null,
                0);
        BlockHitResult fallback = hit(new BlockPos(4, 65, 6));

        assertEquals(new BlockPos(4, 64, 6),
                ShapeSessionInputResolver.resolve(
                        session, fallback, false, false, false, 0, 0,
                        new Vec3(0.0D, 70.0D, 0.0D),
                        new Vec3(1.0D, 0.0D, 0.0D),
                32,
                32).pointB());
        assertEquals(new BlockPos(4, 64, 6),
                ShapeSessionInputResolver.resolve(
                        session, fallback, false, false, false, 0, 0,
                        new Vec3(0.0D, 60.0D, 0.0D),
                        new Vec3(0.0D, -1.0D, 0.0D),
                32,
                32).pointB());
    }

    @Test
    void 脚印微调在解析输入前沿形状平面轴应用() {
        ShapeBuildTypes.Session session = session(
                BuildShape.BOX,
                Direction.NORTH,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                new BlockPos(12, 64, 13),
                5);

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session, null, true, false, false, 2, -1, null, null,
                32,
                32);

        assertEquals(new BlockPos(14, 64, 12), input.pointB());
        assertEquals(5, input.boxHeightOffset());
    }

    @Test
    void 未完成阶段和缺失关键点保持保守空结果() {
        ShapeBuildTypes.Session waitingThird = session(
                BuildShape.BOX,
                Direction.UP,
                ShapeBuildTypes.Phase.NEED_THIRD_POINT,
                new BlockPos(2, 64, 2),
                0);
        assertNull(ShapeSessionInputResolver.resolve(
                waitingThird, null, true, false, false, 0, 0, null, null,
                32,
                32));

        ShapeBuildTypes.Session missingFirst = new ShapeBuildTypes.Session(
                BuildShape.LINE,
                Direction.UP,
                Direction.UP,
                null,
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                0.0D);
        assertNull(ShapeSessionInputResolver.resolve(
                missingFirst, null, false, false, false, 0, 0, null, null,
                32,
                32));
    }

    @Test
    void 极端坐标微调先用long计算并收敛而不回绕() {
        ShapeBuildTypes.Session session = new ShapeBuildTypes.Session(
                BuildShape.BOX,
                Direction.UP,
                Direction.UP,
                new BlockPos(Integer.MAX_VALUE, 64, Integer.MIN_VALUE),
                new BlockPos(Integer.MIN_VALUE, 64, Integer.MAX_VALUE),
                ShapeBuildTypes.Phase.READY_CONFIRM,
                0,
                0.0D);

        ShapeBuildTypes.Input input = ShapeSessionInputResolver.resolve(
                session, null, true, false, false, 1, -1, null, null,
                262_144,
                32);

        assertEquals(new BlockPos(Integer.MAX_VALUE - 262_143, 64,
                Integer.MIN_VALUE + 262_143), input.pointB());
    }

    private static ShapeBuildTypes.Session session(
            BuildShape shape,
            Direction planeFace,
            ShapeBuildTypes.Phase phase,
            BlockPos pointB,
            int heightOffset) {
        return new ShapeBuildTypes.Session(
                shape,
                planeFace,
                Direction.UP,
                new BlockPos(10, 64, 10),
                pointB,
                phase,
                heightOffset,
                0.0D);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false);
    }
}
