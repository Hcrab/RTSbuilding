package com.rtsbuilding.rtsbuilding.uikit.skin;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiNineSliceLayoutTest {
    @Test
    void 始终只生成固定九块() {
        assertEquals(9, layout(new UiRect(0, 0, 500, 300)).size());
    }

    @Test
    void 四角目标尺寸保持源边框尺寸() {
        List<UiNineSliceLayout.Slice> slices = layout(new UiRect(10, 20, 500, 300));
        assertEquals(new UiRect(10, 20, 4, 5), slices.get(0).getTarget());
        assertEquals(new UiRect(504, 314, 6, 6), slices.get(8).getTarget());
    }

    @Test
    void 中心只填充剩余区域() {
        UiNineSliceLayout.Slice center = layout(new UiRect(0, 0, 100, 80)).get(4);
        assertEquals(UiNineSliceLayout.Part.CENTER, center.getPart());
        assertEquals(new UiRect(4, 5, 90, 69), center.getTarget());
    }

    @Test
    void 超小目标拒绝拉伸角() {
        assertThrows(IllegalArgumentException.class,
                () -> layout(new UiRect(0, 0, 9, 20)));
    }

    @Test
    void 边框不能超过源贴图() {
        assertThrows(IllegalArgumentException.class,
                () -> UiNineSliceLayout.calculate(new UiRect(0, 0, 8, 8),
                        new UiRect(0, 0, 100, 100), 5, 2, 5, 2));
    }

    @Test
    void 大目标不会增加四边形数量() {
        assertEquals(layout(new UiRect(0, 0, 100, 100)).size(),
                layout(new UiRect(0, 0, 10000, 10000)).size());
    }

    private static List<UiNineSliceLayout.Slice> layout(UiRect target) {
        return UiNineSliceLayout.calculate(new UiRect(0, 0, 16, 16), target, 4, 5, 6, 6);
    }
}
