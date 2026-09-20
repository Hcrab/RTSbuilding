package com.rtsbuilding.rtsbuilding.test;

import com.rtsbuilding.rtsbuilding.Config;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/** 为既有几何算法测试显式提供原默认建造上限；只替换配置输入，不替换几何实现。 */
public final class ShapeConfigFixture implements BeforeEachCallback, AfterEachCallback {
    private MockedStatic<Config> config;

    @Override
    public void beforeEach(ExtensionContext context) {
        config = mockStatic(Config.class);
        config.when(Config::maxShapeDimension).thenReturn(32);
        config.when(Config::maxShapeRadius).thenReturn(32);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        config.close();
    }
}
