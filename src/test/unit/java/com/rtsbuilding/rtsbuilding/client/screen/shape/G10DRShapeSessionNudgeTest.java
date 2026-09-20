package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 对真实选区会话执行连续微调，验证宽配置不会使正向滚动反向回绕。
 *
 * <p>这里只替换配置读取；会话和加法使用生产实现，不构造游戏窗口或生成巨大形状。
 * 私有偏移通过只读反射观察，避免为了测试暴露新的生产状态写入口。</p>
 */
class G10DRShapeSessionNudgeTest {
    @Test
    void rectangularFootprintKeepsDirectionAtBothIntegerExtremes() throws Exception {
        try (MockedStatic<Config> config = shapeLimits(Integer.MAX_VALUE, Integer.MAX_VALUE)) {
            for (boolean secondary : new boolean[]{false, true}) {
                ShapeSelectionSession owner = session(BuildShape.SQUARE);
                String field = secondary ? "footprintNudgeB" : "footprintNudgeA";
                assertTrue(owner.adjustDimension(Integer.MAX_VALUE, secondary, false));
                assertEquals(Integer.MAX_VALUE - 1, offset(owner, field));
                assertTrue(owner.adjustDimension(10, secondary, false));
                assertEquals(Integer.MAX_VALUE - 1, offset(owner, field));
                assertTrue(owner.adjustDimension(Integer.MIN_VALUE, secondary, false));
                assertEquals(-2, offset(owner, field));
                assertTrue(owner.adjustDimension(Integer.MIN_VALUE, secondary, false));
                assertEquals(-(Integer.MAX_VALUE - 1), offset(owner, field));
                assertTrue(owner.adjustDimension(-10, secondary, false));
                assertEquals(-(Integer.MAX_VALUE - 1), offset(owner, field));
            }
        }
    }

    @Test
    void roundFootprintUsesIndependentRadiusAndNeverWraps() throws Exception {
        try (MockedStatic<Config> config = shapeLimits(32, Integer.MAX_VALUE)) {
            for (boolean secondary : new boolean[]{false, true}) {
                ShapeSelectionSession owner = session(BuildShape.CIRCLE);
                String field = secondary ? "footprintNudgeB" : "footprintNudgeA";
                owner.adjustDimension(Integer.MAX_VALUE, secondary, false);
                owner.adjustDimension(1, secondary, false);
                assertEquals(Integer.MAX_VALUE, offset(owner, field));
                owner.adjustDimension(Integer.MIN_VALUE, secondary, false);
                owner.adjustDimension(Integer.MIN_VALUE, secondary, false);
                assertEquals(-Integer.MAX_VALUE, offset(owner, field));
                owner.adjustDimension(-1, secondary, false);
                assertEquals(-Integer.MAX_VALUE, offset(owner, field));
            }
        }
    }

    @Test
    void heightSaturatesWithoutReversingAndRetainsDefaultLimit() {
        try (MockedStatic<Config> config = shapeLimits(Integer.MAX_VALUE, 32)) {
            ShapeSelectionSession owner = session(BuildShape.BOX);
            assertTrue(owner.adjustHeight(Integer.MAX_VALUE));
            assertTrue(owner.adjustHeight(10));
            assertEquals(Integer.MAX_VALUE - 1, owner.current().boxHeightOffset());
            owner.adjustHeight(Integer.MIN_VALUE);
            owner.adjustHeight(Integer.MIN_VALUE);
            owner.adjustHeight(-10);
            assertEquals(-(Integer.MAX_VALUE - 1), owner.current().boxHeightOffset());

            config.when(Config::maxShapeDimension).thenReturn(32);
            ShapeSelectionSession normal = session(BuildShape.BOX);
            normal.adjustHeight(5);
            normal.adjustHeight(-2);
            assertEquals(3, normal.current().boxHeightOffset());
            normal.adjustHeight(100);
            assertEquals(31, normal.current().boxHeightOffset());
            normal.adjustHeight(-100);
            assertEquals(-31, normal.current().boxHeightOffset());
        }
    }

    private static MockedStatic<Config> shapeLimits(int dimension, int radius) {
        MockedStatic<Config> config = mockStatic(Config.class);
        config.when(Config::maxShapeDimension).thenReturn(dimension);
        config.when(Config::maxShapeRadius).thenReturn(radius);
        return config;
    }

    private static ShapeSelectionSession session(BuildShape shape) {
        ShapeSelectionSession owner = new ShapeSelectionSession();
        owner.replace(new ShapeBuildTypes.Session(
                shape, Direction.UP, Direction.UP, BlockPos.ZERO, new BlockPos(1, 0, 1),
                shape == BuildShape.BOX ? ShapeBuildTypes.Phase.NEED_THIRD_POINT
                        : ShapeBuildTypes.Phase.READY_CONFIRM,
                0, 0.0D));
        return owner;
    }

    private static int offset(ShapeSelectionSession owner, String name) throws Exception {
        Field field = ShapeSelectionSession.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(owner);
    }
}
